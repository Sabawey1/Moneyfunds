package com.fundtracker.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A "logical account" is what the user thinks of as one account.
 * It can group several SMS identifiers — e.g. account ••4215 and its
 * debit card ••5534 are the same money, so they get linked together and
 * counted once.
 */
data class LogicalAccount(
    val id: String,
    val name: String,
    val type: String,            // "Bank", "Card", "Cash", "Wallet"
    val linkedIds: List<String>, // last-4 identifiers from SMS; empty for cash
    val manualBalance: Double?   // user-set balance; null = use bank SMS balance
)

object AccountRegistry {
    private const val PREFS = "fundtracker_accounts"
    private const val KEY = "accounts"

    fun registered(ctx: Context): List<LogicalAccount> {
        val arr = JSONArray(prefs(ctx).getString(KEY, "[]"))
        val out = ArrayList<LogicalAccount>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ids = ArrayList<String>()
            val ja = o.optJSONArray("ids") ?: JSONArray()
            for (j in 0 until ja.length()) ids.add(ja.getString(j))
            out.add(
                LogicalAccount(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    type = o.optString("type", "Bank"),
                    linkedIds = ids,
                    manualBalance = if (o.has("balance")) o.getDouble("balance") else null
                )
            )
        }
        return out
    }

    private fun save(ctx: Context, list: List<LogicalAccount>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("name", a.name)
                put("type", a.type)
                put("ids", JSONArray(a.linkedIds))
                if (a.manualBalance != null) put("balance", a.manualBalance)
            })
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    /** Create an account and return its new id. */
    fun add(ctx: Context, name: String, type: String,
            linkedIds: List<String> = emptyList(), balance: Double? = null): String {
        val list = registered(ctx).toMutableList()
        val id = "la_" + System.currentTimeMillis()
        list.add(LogicalAccount(id, name.ifBlank { "Account" }, type, linkedIds, balance))
        save(ctx, list)
        return id
    }

    fun remove(ctx: Context, id: String) =
        save(ctx, registered(ctx).filter { it.id != id })

    fun rename(ctx: Context, id: String, name: String) =
        save(ctx, registered(ctx).map { if (it.id == id) it.copy(name = name) else it })

    fun setBalance(ctx: Context, id: String, value: Double?) =
        save(ctx, registered(ctx).map { if (it.id == id) it.copy(manualBalance = value) else it })

    fun addLinkedIds(ctx: Context, id: String, ids: List<String>) =
        save(ctx, registered(ctx).map {
            if (it.id == id) it.copy(linkedIds = (it.linkedIds + ids).distinct()) else it
        })

    fun unlinkId(ctx: Context, id: String, rawId: String) =
        save(ctx, registered(ctx).map {
            if (it.id == id) it.copy(linkedIds = it.linkedIds - rawId) else it
        })

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
