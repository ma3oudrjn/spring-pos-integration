# مستندات راه اندازی و اتصال به دستگاه کارتخوان سپ (SEP PC-POS)

این مستند مراحل پیکربندی سخت‌افزاری و پیاده‌سازی نرم‌افزاری جهت اتصال به سرویس Central PC-POS شرکت پرداخت الکترونیک سامان (SEP) را توضیح می‌دهد.

## 📋 پیش‌نیازها

قبل از شروع، اطمینان حاصل کنید که موارد زیر را در اختیار دارید:
*   **Username** (نام کاربری)
*   **Password** (رمز عبور)
*   **TerminalID** (شماره ترمینال - قابل مشاهده روی دستگاه)


## 🛠 پیکربندی دستگاه کارتخوان (POS Configuration)

برای فعال‌سازی پروتکل ارتباطی روی دستگاه پوز، مراحل زیر را طی کنید:

1.  وارد منوی دستگاه شوید.
2.  به مسیر زیر بروید:
    `۱. گزارشات` > `۲. پیکربندی` > `۳. تنظیمات پروتکل`
3.  بررسی کنید که گزینه **Central PC-POS** فعال باشد.

### اگر پروتکل فعال نبود:
1.  عدد `6` را فشار دهید.
2.  گزینه `تنظیمات پروتکل` را انتخاب کنید.
3.  یک کانکشن با آپشن **تک حسابی** ایجاد کنید.


### 🆔 مشاهده Terminal ID
برای مشاهده شماره ترمینال، دکمه **زرد رنگ** روی دستگاه را فشار دهید.

---

## 💻 راهنمای پیاده‌سازی API

فرآیند پرداخت در ۴ مرحله کلی انجام می‌شود.

### مرحله ۱: دریافت توکن (Authentication)
ابتدا باید یک Access Token دریافت کرده و آن را کش (Cache) کنید.

*   **URL:** `https://idn.seppay.ir/connect/token`
*   **Method:** `POST`
*   **Auth (Basic):** `username: roclient` | `password: secret`
*   **Content-Type:** `application/x-www-form-urlencoded`

**Body Parameters:**
| Key | Value |
| :--- | :--- |
| `grant_type` | `password` |
| `username` | *نام کاربری دریافتی* |
| `password` | *رمز عبور دریافتی* |
| `scope` | `SepCentralPcPos openid` |

**نمونه خروجی:** توکن دریافتی را باید در هدر `Authorization` به صورت `Bearer {Token}` در مراحل بعد استفاده کنید.

---

### مرحله ۲: تولید شناسه یکتا (Generate Identifier)
برای هر تراکنش نیاز به یک شناسه یکتا (UUID) دارید.

*   **URL:** `https://cpcpos.seppay.ir/v1/PcPosTransaction/ReciveIdentifier`
*   **Method:** `POST`
*   **Header:** `Authorization: Bearer {Access_Token}`

**Body:** (خالی)

**خروجی:** یک رشته UUID که باید در مرحله پرداخت استفاده شود.

---

### مرحله ۳: شروع پرداخت (Start Payment)
ارسال فرمان پرداخت به دستگاه کارتخوان.

*   **URL:** `https://cpcpos.seppay.ir/v1/PcPosTransaction/StartPayment`
*   **Method:** `POST`
*   **Header:**
    *   `Content-Type: application/json`
    *   `Authorization: Bearer {Access_Token}`

**Body Example:**
```json
{
    "TerminalID": "13947404",
    "AccountType": 0,
    "TransactionType": 0,
    "ResNum": "654321",
    "Identifier": "UUID_FROM_STEP_2",
    "Amount": "10000",
    "RefrenceData": "OptionalData",
    "UserNotifiable": {
        "PrintItems": [
            {
                "Item": "Title",
                "Value": "Value",
                "Alignment": 2,
                "ReceiptType": 0
            }
        ]
    }
}