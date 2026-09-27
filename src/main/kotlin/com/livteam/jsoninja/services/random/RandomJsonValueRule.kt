package com.livteam.jsoninja.services.random

internal sealed interface RandomJsonValueRule {
    data class Generated(val kind: RandomJsonValueKind) : RandomJsonValueRule
    data class IntegerRange(val minimum: Long, val maximum: Long) : RandomJsonValueRule
    data class DecimalRange(val minimumUnscaled: Long, val maximumUnscaled: Long, val scale: Int) : RandomJsonValueRule
    data class Choice(val values: List<String>) : RandomJsonValueRule
}

internal enum class RandomJsonValueKind {
    TEXT, SHORT_TEXT, LONG_TEXT, INTEGER, DECIMAL, BOOLEAN, COUNT, UNIQUE_ID, UUID,
    REFERENCE_CODE, NAME, FIRST_NAME, LAST_NAME, USERNAME, EMAIL, PHONE, URL, IMAGE_URL,
    DATE, BIRTH_DATE, DATE_TIME, TIME, EPOCH_SECONDS, EPOCH_MILLIS,
    COMPANY, DEPARTMENT, JOB_TITLE, STREET_ADDRESS, STREET_NAME, CITY, STATE, COUNTRY,
    POSTAL_CODE, BUILDING_NUMBER, COUNTRY_CODE, LANGUAGE_CODE, LANGUAGE_NAME, LOCALE, TIME_ZONE,
    CURRENCY_CODE, CURRENCY_NAME, CURRENCY_SYMBOL, GEOHASH,
    PRODUCT_NAME, BRAND, MATERIAL, SKU, COLOR_NAME, HEX_COLOR,
    FILE_NAME, FILE_EXTENSION, MIME_TYPE, FILE_PATH,
    IPV4, IPV6, MAC_ADDRESS, HOSTNAME, HTTP_PATH, HTTP_STATUS,
    DEVICE_MODEL, MANUFACTURER, OPERATING_SYSTEM, BROWSER, USER_AGENT,
    APP_NAME, VERSION, CLOUD_REGION, CLOUD_RESOURCE, SERVICE_NAME,
    HEX_16, HEX_32, HEX_40, HEX_64, HEX_128,
}
