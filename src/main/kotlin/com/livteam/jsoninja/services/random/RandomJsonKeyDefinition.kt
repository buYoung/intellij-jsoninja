package com.livteam.jsoninja.services.random

internal data class RandomJsonKeyDefinition(
    val key: String,
    val category: RandomJsonKeyCategory,
    val rule: RandomJsonValueRule?,
    val semanticGroup: String = key,
    val childDomain: RandomJsonDomain? = null,
)

internal enum class RandomJsonKeyCategory(val containerLevels: Int = 0) {
    IDENTIFIER, REFERENCE, PERSON_NAME, ACCOUNT, EMAIL, PHONE, URL, SHORT_TEXT, LONG_TEXT,
    LABEL, BOOLEAN, STATUS, ROLE, COUNT, PAGINATION, MONEY, CURRENCY, RATIO, SCORE,
    MEASUREMENT, DATE, DATE_TIME, TIME, EPOCH, DURATION, ORGANIZATION, JOB, ADDRESS,
    GEO, LOCALE, PRODUCT, COLOR, FILE, MEDIA, NETWORK, HTTP, DEVICE, SOFTWARE, CLOUD,
    LOG, TASK, HASH, OBJECT(1), VALUE_ARRAY(1), OBJECT_ARRAY(2),
}

internal enum class RandomJsonDomain(vararg categories: RandomJsonKeyCategory) {
    ACCOUNT(RandomJsonKeyCategory.PERSON_NAME, RandomJsonKeyCategory.ACCOUNT, RandomJsonKeyCategory.EMAIL,
        RandomJsonKeyCategory.PHONE, RandomJsonKeyCategory.ROLE, RandomJsonKeyCategory.ADDRESS),
    ORGANIZATION(RandomJsonKeyCategory.ORGANIZATION, RandomJsonKeyCategory.JOB, RandomJsonKeyCategory.PERSON_NAME,
        RandomJsonKeyCategory.EMAIL, RandomJsonKeyCategory.PHONE, RandomJsonKeyCategory.ADDRESS),
    PRODUCT(RandomJsonKeyCategory.PRODUCT, RandomJsonKeyCategory.MONEY, RandomJsonKeyCategory.CURRENCY,
        RandomJsonKeyCategory.COUNT, RandomJsonKeyCategory.COLOR, RandomJsonKeyCategory.MEDIA),
    ORDER(RandomJsonKeyCategory.REFERENCE, RandomJsonKeyCategory.MONEY, RandomJsonKeyCategory.CURRENCY,
        RandomJsonKeyCategory.STATUS, RandomJsonKeyCategory.DATE_TIME, RandomJsonKeyCategory.COUNT),
    SHIPPING(RandomJsonKeyCategory.REFERENCE, RandomJsonKeyCategory.ADDRESS, RandomJsonKeyCategory.GEO,
        RandomJsonKeyCategory.STATUS, RandomJsonKeyCategory.DURATION, RandomJsonKeyCategory.MEASUREMENT),
    CONTENT(RandomJsonKeyCategory.SHORT_TEXT, RandomJsonKeyCategory.LONG_TEXT, RandomJsonKeyCategory.ACCOUNT,
        RandomJsonKeyCategory.MEDIA, RandomJsonKeyCategory.LABEL, RandomJsonKeyCategory.SCORE),
    SERVER(RandomJsonKeyCategory.HTTP, RandomJsonKeyCategory.NETWORK, RandomJsonKeyCategory.LOG,
        RandomJsonKeyCategory.DURATION, RandomJsonKeyCategory.HASH, RandomJsonKeyCategory.FILE),
    TASK(RandomJsonKeyCategory.TASK, RandomJsonKeyCategory.STATUS, RandomJsonKeyCategory.COUNT,
        RandomJsonKeyCategory.DATE_TIME, RandomJsonKeyCategory.DURATION, RandomJsonKeyCategory.TIME),
    CLOUD(RandomJsonKeyCategory.CLOUD, RandomJsonKeyCategory.SOFTWARE, RandomJsonKeyCategory.NETWORK,
        RandomJsonKeyCategory.LOG, RandomJsonKeyCategory.DEVICE, RandomJsonKeyCategory.HASH),
    ANALYTICS(RandomJsonKeyCategory.COUNT, RandomJsonKeyCategory.RATIO, RandomJsonKeyCategory.SCORE,
        RandomJsonKeyCategory.DATE, RandomJsonKeyCategory.EPOCH, RandomJsonKeyCategory.PAGINATION);

    val preferredCategories: Set<RandomJsonKeyCategory> = categories.toSet() + setOf(
        RandomJsonKeyCategory.IDENTIFIER, RandomJsonKeyCategory.BOOLEAN, RandomJsonKeyCategory.DATE_TIME,
    )
}
