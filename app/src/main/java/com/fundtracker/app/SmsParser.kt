package com.fundtracker.app

import android.content.Context
import android.net.Uri

enum class TxnType { DEBIT, CREDIT }

data class Txn(
    val sender: String,
    val body: String,
    val date: Long,
    val amount: Double,
    val currency: String,
    val type: TxnType,
    val account: String?,
    val merchant: String?,
    val balance: Double?,
    val category: String,
    val manual: Boolean = false,
    val id: Long = 0
)

data class RawMsg(val sender: String, val body: String, val date: Long)

data class ScanResult(val txns: List<Txn>, val missed: List<RawMsg>)

/** User-editable keyword rules stored in SharedPreferences. */
object RulesStore {
    private const val PREFS = "fundtracker_rules"
    private const val KEY_DEBIT = "extra_debit_words"
    private const val KEY_CREDIT = "extra_credit_words"

    fun extraDebitWords(ctx: Context): MutableSet<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_DEBIT, emptySet())!!.toMutableSet()

    fun extraCreditWords(ctx: Context): MutableSet<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_CREDIT, emptySet())!!.toMutableSet()

    fun addWord(ctx: Context, word: String, debit: Boolean) {
        val key = if (debit) KEY_DEBIT else KEY_CREDIT
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(key, emptySet())!!.toMutableSet()
        set.add(word.trim().lowercase())
        prefs.edit().putStringSet(key, set).apply()
    }

    fun removeWord(ctx: Context, word: String, debit: Boolean) {
        val key = if (debit) KEY_DEBIT else KEY_CREDIT
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(key, emptySet())!!.toMutableSet()
        set.remove(word)
        prefs.edit().putStringSet(key, set).apply()
    }
}

object SmsParser {

    private val amountRegex = Regex(
        """(?i)(?<![A-Za-z])(EGP|L\.E\.?|LE|USD|\$|EUR|GBP|AED|SAR|INR|Rs\.?|₹)\s*\.?\s*([\d,]+(?:\.\d{1,2})?)"""
    )

    // Arabic format: amount comes BEFORE the currency, e.g. "مبلغ 6199.00 جم"
    private val arabicAmountRegex = Regex(
        """([\d,]+(?:\.\d{1,2})?)\s*(?:جم|ج\.م\.?|جنيه)"""
    )

    private val accountRegexes = listOf(
        // English: "card ending with#3547", "account ending 4215"
        Regex("""(?i)(?:a/c|ac|acct|account|card)[^.\n]{0,25}?ending\s*(?:with)?\s*#?\s*[xX*]*(\d{2,6})"""),
        Regex("""(?i)(?:a/c|ac|acct|account|card)\s*(?:no\.?)?\s*[#xX*]+\s*(\d{2,6})"""),
        // Arabic: "المنتهية بـ 3547", "حسابك المنتهي بـ ********4215"
        Regex("""المنتهي[ةه]?\s*ب[ـ]?\s*[*xX\s]*(\d{2,6})""")
    )

    private val balanceRegexes = listOf(
        Regex("""(?i)(?:avl|avail(?:able)?|net)\.?\s*(?:bal|balance|limit)\.?\s*(?:is)?\s*[:\-]?\s*(?:EGP|L\.E\.?|LE|INR|Rs\.?|₹|\$)?\s*\.?\s*([\d,]+(?:\.\d{1,2})?)"""),
        // Arabic: "الرصيد المتاح EGP 6002.40" or "الرصيد المتاح 6002.40 جم"
        Regex("""الرصيد\s*(?:المتاح|الحالي|المتبقي)?\s*[:\-]?\s*(?:EGP|L\.E\.?|LE)?\s*\.?\s*([\d,]+(?:\.\d{1,2})?)""")
    )

    private val merchantRegexes = listOf(
        Regex("""(?i)\b(?:at|to|from)\s+([A-Za-z][A-Za-z0-9 &._\-]{2,28}?)(?=\s+on\b|\s+via\b|\s+ref\b|\s+upi\b|[.,;]|$)"""),
        // Arabic: "من ALXB في ..." — Latin merchant / ATM code after "من"
        Regex("""من\s+([A-Za-z][A-Za-z0-9&._\-]{1,28})""")
    )

    private val defaultDebitWords = setOf(
        "debited", "debit", "spent", "withdrawn", "withdrawal", "paid",
        "purchase", "deducted", "sent", "transferred", "txn of",
        "was used", "used for", "you have sent", "transfer of",
        "payment of", "charged",
        // Arabic: deduction, withdrawal, purchase, transfer FROM your account
        "تم خصم", "خصم", "سحب", "شراء", "من حسابك"
    )

    private val defaultCreditWords = setOf(
        "credited", "credit", "received", "deposited", "refund",
        "refunded", "added", "cashback", "you have received", "incoming transfer",
        // Arabic: card settlement, deposit, transfer TO your account
        "تم سداد", "ايداع", "إيداع", "إضافة", "الى حسابك", "إلى حسابك", "وصلك"
    )

    private val bankHintWords = setOf(
        "a/c", "ac ", "acct", "account", "card", "bank", "cib",
        "instapay", "ipn", "bal", "balance", "atm", "wallet",
        "upi", "neft", "imps", "rtgs",
        // Arabic hints: account, card, transfer, EGP
        "حساب", "بطاق", "تحويل", "جم", "جنيه"
    )

    private val categories = mapOf(
        // "تحويل لحظي" = instant transfer = InstaPay
        "InstaPay" to listOf("instapay", "ipn", "تحويل لحظي", "انستاباي"),
        "Card Payment" to listOf("سداد", "card payment", "credit card bill"),
        "Food" to listOf("talabat", "elmenus", "breadfast", "mcdonald", "kfc", "pizza", "restaurant", "cafe", "koshary", "food"),
        "Transport" to listOf("uber", "careem", "swvl", "indrive", "fuel", "petrol", "gas station", "metro"),
        "Shopping" to listOf("amazon", "noon", "jumia", "carrefour", "spinneys", "hyper", "mall", "store", "market", "b.tech"),
        "Bills" to listOf("vodafone", "orange", "etisalat", "we ", "fawry", "electricity", "internet", "recharge", "bill", "water"),
        "Entertainment" to listOf("netflix", "spotify", "shahid", "anghami", "prime", "youtube", "cinema", "vox"),
        "ATM" to listOf("atm", "cash withdrawal", "سحب"),
        "Transfer" to listOf("transfer", "ach", "swift", "تحويل")
    )

    private fun parseNumber(s: String): Double? =
        s.replace(",", "").toDoubleOrNull()

    private fun categorize(body: String): String {
        val lower = body.lowercase()
        for ((cat, words) in categories) {
            if (words.any { lower.contains(it) }) return cat
        }
        return "Other"
    }

    /** Reads the entire SMS inbox and parses every financial message. */
    fun scan(ctx: Context): ScanResult {
        val extraDebit = RulesStore.extraDebitWords(ctx)
        val extraCredit = RulesStore.extraCreditWords(ctx)
        val debitWords = defaultDebitWords + extraDebit
        val creditWords = defaultCreditWords + extraCredit

        val txns = mutableListOf<Txn>()
        val missed = mutableListOf<RawMsg>()

        val cursor = ctx.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"),
            null, null, "date DESC"
        )

        cursor?.use { c ->
            val iAddr = c.getColumnIndex("address")
            val iBody = c.getColumnIndex("body")
            val iDate = c.getColumnIndex("date")
            while (c.moveToNext()) {
                val sender = c.getString(iAddr) ?: ""
                val body = c.getString(iBody) ?: ""
                val date = c.getLong(iDate)
                val lower = body.lowercase()

                // Skip OTP-only messages
                if (lower.contains("otp") || lower.contains("one time password")) continue

                val enMatch = amountRegex.find(body)
                val arMatch = if (enMatch == null) arabicAmountRegex.find(body) else null
                val amount: Double
                val currency: String
                if (enMatch != null) {
                    amount = parseNumber(enMatch.groupValues[2]) ?: continue
                    val rawCur = enMatch.groupValues[1].uppercase()
                    currency = when {
                        rawCur.startsWith("L.E") || rawCur == "LE" || rawCur == "EGP" -> "EGP"
                        rawCur.startsWith("RS") || rawCur == "₹" -> "Rs"
                        else -> rawCur
                    }
                } else if (arMatch != null) {
                    amount = parseNumber(arMatch.groupValues[1]) ?: continue
                    currency = "EGP"
                } else continue

                val isDebit = debitWords.any { lower.contains(it) }
                val isCredit = creditWords.any { lower.contains(it) }

                if (!isDebit && !isCredit) {
                    // Has an amount and looks bank-related, but we couldn't
                    // classify it -> surface it in the "Missed" tab.
                    if (bankHintWords.any { lower.contains(it) }) {
                        missed.add(RawMsg(sender, body, date))
                    }
                    continue
                }

                val type = if (isDebit && !isCredit) TxnType.DEBIT
                else if (isCredit && !isDebit) TxnType.CREDIT
                else if (lower.indexOfAny(debitWords.toList()) != -1 &&
                    debitWords.minOf { w -> lower.indexOf(w).let { if (it < 0) Int.MAX_VALUE else it } } <
                    creditWords.minOf { w -> lower.indexOf(w).let { if (it < 0) Int.MAX_VALUE else it } }
                ) TxnType.DEBIT else TxnType.CREDIT

                val account = accountRegexes.firstNotNullOfOrNull {
                    it.find(body)?.groupValues?.get(1)
                }
                val balance = balanceRegexes.firstNotNullOfOrNull {
                    it.find(body)?.groupValues?.get(1)
                }?.let { parseNumber(it) }
                val merchant = merchantRegexes.firstNotNullOfOrNull {
                    it.find(body)?.groupValues?.get(1)
                }?.trim()

                txns.add(
                    Txn(sender, body, date, amount, currency, type,
                        account, merchant, balance, categorize(body),
                        manual = false, id = date)
                )
            }
        }
        return ScanResult(txns, missed)
    }
}

private fun String.indexOfAny(words: List<String>): Int {
    var best = -1
    for (w in words) {
        val i = indexOf(w)
        if (i >= 0 && (best == -1 || i < best)) best = i
    }
    return best
}
