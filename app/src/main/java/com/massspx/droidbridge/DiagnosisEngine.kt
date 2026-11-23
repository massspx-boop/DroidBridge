package com.massspx.droidbridge

object DiagnosisEngine {
    fun explain(raw: String): String {
        val s = raw.lowercase().replace('_', ' ').replace('-', ' ')
        return when {
            "version downgrade" in s || "downgrade" in s -> "ဖုန်းထဲရှိ App ဗားရှင်းက ပိုသစ်နေသည်။ မူလထက်နိမ့်သော version ကို Android က ပိတ်နိုင်သည်။"
            "update incompatible" in s || "signatures do not match" in s || "signature" in s ->
                "APK လက်မှတ် (signing certificate) မကိုက်ညီပါ။ မူရင်း developer ထုတ် APK ကိုသာ အသုံးပြုပါ။"
            "no matching abis" in s || "abi" in s -> "CPU Architecture မကိုက်ညီနိုင်ပါ။ ဖုန်း၏ ABI နှင့် ကိုက်ညီသော APK ကို ရွေးပါ။"
            "parse" in s || "invalid apk" in s -> "APK ဖိုင်ပျက်နေခြင်း၊ မပြည့်စုံခြင်း သို့မဟုတ် format မကိုက်ညီခြင်း ဖြစ်နိုင်သည်။ ပြန်ကူးပြီး SHA-256 စစ်ပါ။"
            "blocked" in s || "unknown sources" in s || "user action" in s ->
                "Android လုံခြုံရေးက တပ်ဆင်မှုကို ပိတ်ထားနိုင်သည်။ DroidBridge အတွက် Install unknown apps ခွင့်ပြုချက်ကို Settings မှ ခွင့်ပြုပါ။"
            "insufficient storage" in s || "not enough space" in s ->
                "ဖုန်းတွင် နေရာမလုံလောက်ပါ။ မလိုအပ်သောဖိုင်များဖယ်ရှားပြီး ထပ်စမ်းပါ။"
            "sdk" in s || "version" in s -> "Android Version သို့မဟုတ် SDK မကိုက်ညီနိုင်ပါ။ APK ၏ minSdk နှင့် ဖုန်း Android Version ကို စစ်ပါ။"
            else -> "အမှားအကြောင်းအရာကို အပြည့်အစုံဖတ်၍ APK integrity, signature, SDK, CPU ABI နှင့် storage ကို တစ်ဆင့်ချင်း စစ်ဆေးပါ။ AI diagnosis သည် အကြံပြုချက်သာဖြစ်ပြီး Android ၏ တပ်ဆင်ခွင့်ကို မကျော်လွှားနိုင်ပါ။"
        }
    }
}
