package com.fundtracker.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Manual transactions the bank SMS can't provide (cash spending, money you add). */
object ManualStore {
    private const val PREFS = "fundtracker_manual"
    private const val KEY_TXNS = "manual_txns"

    fun manualTxns(ctx: Context): List<Txn> {
        val raw = prefs(ctx).getString(KEY_TXNS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = ArrayList<Txn>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("account").ifBlank { null }
            out.add(
                Txn(
                    sender = name ?: "Manual",
                    body = o.optString("note", ""),
                    date = o.getLong("date"),
                    amount = o.getDouble("amount"),
                    currency = "EGP",
                    type = if (o.getString("type") == "DEBIT") TxnType.DEBIT else TxnType.CREDIT,
                    account = name,
                    merchant = o.optString("note").ifBlank { null },
                    balance = null,
                    category = o.optString("category", "Other"),
                    manual = true,
                    id = o.getLong("id")
                )
            )
        }
        return out
    }

    /** Records a manual transaction. Balance changes are handled by the caller via AccountRegistry. */
    fun addManual(
        ctx: Context, amount: Double, type: TxnType,
        accountName: String, category: String, note: String, date: Long
    ) {
        val arr = JSONArray(prefs(ctx).getString(KEY_TXNS, "[]"))
        arr.put(JSONObject().apply {
            put("id", System.currentTimeMillis())
            put("amount", amount)
            put("type", if (type == TxnType.DEBIT) "DEBIT" else "CREDIT")
            put("account", accountName)
            put("category", category)
            put("note", note)
            put("date", date)
        })
        prefs(ctx).edit().putString(KEY_TXNS, arr.toString()).apply()
    }

    fun deleteManual(ctx: Context, id: Long) {
        val arr = JSONArray(prefs(ctx).getString(KEY_TXNS, "[]"))
        val kept = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getLong("id") != id) kept.put(o)
        }
        prefs(ctx).edit().putString(KEY_TXNS, kept.toString()).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
