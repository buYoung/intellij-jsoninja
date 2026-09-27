package com.livteam.jsoninja.services.random

import net.datafaker.Faker
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

internal class RandomJsonPrimitiveGenerator(
    private val random: Random,
    private val faker: Faker,
) {
    fun generate(rule: RandomJsonValueRule): Any = when (rule) {
        is RandomJsonValueRule.IntegerRange -> random.nextLong(rule.minimum, rule.maximum + 1)
        is RandomJsonValueRule.DecimalRange -> BigDecimal.valueOf(
            random.nextLong(rule.minimumUnscaled, rule.maximumUnscaled + 1), rule.scale
        )
        is RandomJsonValueRule.Choice -> rule.values.random(random)
        is RandomJsonValueRule.Generated -> generate(rule.kind)
    }

    private fun generate(kind: RandomJsonValueKind): Any = when (kind) {
        RandomJsonValueKind.TEXT -> faker.lorem().word()
        RandomJsonValueKind.SHORT_TEXT -> faker.lorem().sentence(4)
        RandomJsonValueKind.LONG_TEXT -> faker.lorem().paragraph(3)
        RandomJsonValueKind.INTEGER -> random.nextInt(1, 100_001)
        RandomJsonValueKind.DECIMAL -> BigDecimal.valueOf(random.nextLong(0, 500_001), 2)
        RandomJsonValueKind.BOOLEAN -> random.nextBoolean()
        RandomJsonValueKind.COUNT -> random.nextInt(0, 1_001)
        RandomJsonValueKind.UNIQUE_ID -> error("Unique IDs require an array scope")
        RandomJsonValueKind.UUID -> faker.internet().uuid()
        RandomJsonValueKind.REFERENCE_CODE -> randomCharacters(12, "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
        RandomJsonValueKind.NAME -> faker.name().fullName()
        RandomJsonValueKind.FIRST_NAME -> faker.name().firstName()
        RandomJsonValueKind.LAST_NAME -> faker.name().lastName()
        RandomJsonValueKind.USERNAME -> faker.credentials().username()
        RandomJsonValueKind.EMAIL -> faker.internet().safeEmailAddress()
        RandomJsonValueKind.PHONE -> faker.phoneNumber().phoneNumberInternational()
        RandomJsonValueKind.URL -> "https://${faker.internet().domainWord()}.example.com/${faker.internet().slug()}"
        RandomJsonValueKind.IMAGE_URL -> "https://images.example.com/${randomCharacters(12, HEX_DIGITS)}.png"
        RandomJsonValueKind.DATE -> randomInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString()
        RandomJsonValueKind.BIRTH_DATE -> LocalDate.of(1940, 1, 1).plusDays(random.nextLong(0, 24_000)).toString()
        RandomJsonValueKind.DATE_TIME -> randomInstant().toString()
        RandomJsonValueKind.TIME -> LocalTime.ofSecondOfDay(random.nextLong(86_400)).format(TIME_FORMAT)
        RandomJsonValueKind.EPOCH_SECONDS -> random.nextLong(DATE_START_SECONDS, DATE_END_SECONDS)
        RandomJsonValueKind.EPOCH_MILLIS -> random.nextLong(DATE_START_SECONDS * 1_000, DATE_END_SECONDS * 1_000)
        RandomJsonValueKind.COMPANY -> faker.company().name()
        RandomJsonValueKind.DEPARTMENT -> departments.random(random)
        RandomJsonValueKind.JOB_TITLE -> faker.job().title()
        RandomJsonValueKind.STREET_ADDRESS -> faker.address().streetAddress()
        RandomJsonValueKind.STREET_NAME -> faker.address().streetName()
        RandomJsonValueKind.CITY -> faker.address().cityName()
        RandomJsonValueKind.STATE -> faker.address().state()
        RandomJsonValueKind.COUNTRY -> faker.country().name()
        RandomJsonValueKind.POSTAL_CODE -> faker.address().zipCode()
        RandomJsonValueKind.BUILDING_NUMBER -> faker.address().buildingNumber()
        RandomJsonValueKind.COUNTRY_CODE -> faker.country().countryCode2().uppercase(Locale.ROOT)
        RandomJsonValueKind.LANGUAGE_CODE -> languages.random(random).first
        RandomJsonValueKind.LANGUAGE_NAME -> languages.random(random).second
        RandomJsonValueKind.LOCALE -> locales.random(random)
        RandomJsonValueKind.TIME_ZONE -> timeZones.random(random)
        RandomJsonValueKind.CURRENCY_CODE -> faker.money().currencyCode().uppercase(Locale.ROOT)
        RandomJsonValueKind.CURRENCY_NAME -> faker.money().currency()
        RandomJsonValueKind.CURRENCY_SYMBOL -> currencySymbols.random(random)
        RandomJsonValueKind.GEOHASH -> randomCharacters(8, "0123456789bcdefghjkmnpqrstuvwxyz")
        RandomJsonValueKind.PRODUCT_NAME -> faker.commerce().productName()
        RandomJsonValueKind.BRAND -> faker.commerce().brand()
        RandomJsonValueKind.MATERIAL -> faker.commerce().material()
        RandomJsonValueKind.SKU -> randomCharacters(3, "ABCDEFGHIJKLMNOPQRSTUVWXYZ") + "-" + randomCharacters(8, "0123456789")
        RandomJsonValueKind.COLOR_NAME -> faker.color().name()
        RandomJsonValueKind.HEX_COLOR -> "#${randomCharacters(6, HEX_DIGITS)}"
        RandomJsonValueKind.FILE_NAME -> faker.file().fileName(null, null, null, "/").substringAfterLast('/')
        RandomJsonValueKind.FILE_EXTENSION -> faker.file().extension()
        RandomJsonValueKind.MIME_TYPE -> faker.file().mimeType()
        RandomJsonValueKind.FILE_PATH -> "/data/${resourceName()}/${faker.file().fileName(null, null, null, "/").substringAfterLast('/')}"
        RandomJsonValueKind.IPV4 -> faker.internet().ipV4Address()
        RandomJsonValueKind.IPV6 -> faker.internet().ipV6Address()
        RandomJsonValueKind.MAC_ADDRESS -> faker.internet().macAddress()
        RandomJsonValueKind.HOSTNAME -> "${resourceName()}.example.com"
        RandomJsonValueKind.HTTP_PATH -> "/api/${resourceName()}/${random.nextInt(1, 10_000)}"
        RandomJsonValueKind.HTTP_STATUS -> httpStatusCodes.random(random)
        RandomJsonValueKind.DEVICE_MODEL -> faker.device().modelName()
        RandomJsonValueKind.MANUFACTURER -> faker.device().manufacturer()
        RandomJsonValueKind.OPERATING_SYSTEM -> faker.computer().operatingSystem()
        RandomJsonValueKind.BROWSER -> browsers.random(random)
        RandomJsonValueKind.USER_AGENT -> faker.internet().userAgent()
        RandomJsonValueKind.APP_NAME -> faker.app().name()
        RandomJsonValueKind.VERSION -> "${random.nextInt(1, 10)}.${random.nextInt(0, 20)}.${random.nextInt(0, 50)}"
        RandomJsonValueKind.CLOUD_REGION -> cloudRegions.random(random)
        RandomJsonValueKind.CLOUD_RESOURCE -> resourceName()
        RandomJsonValueKind.SERVICE_NAME -> "${resourceName()}-service"
        RandomJsonValueKind.HEX_16 -> randomCharacters(16, HEX_DIGITS)
        RandomJsonValueKind.HEX_32 -> randomCharacters(32, HEX_DIGITS)
        RandomJsonValueKind.HEX_40 -> randomCharacters(40, HEX_DIGITS)
        RandomJsonValueKind.HEX_64 -> randomCharacters(64, HEX_DIGITS)
        RandomJsonValueKind.HEX_128 -> randomCharacters(128, HEX_DIGITS)
    }

    private fun randomCharacters(length: Int, alphabet: String): String =
        CharArray(length) { alphabet[random.nextInt(alphabet.length)] }.concatToString()

    private fun resourceName(): String = faker.internet().slug().lowercase(Locale.ENGLISH)
        .replace(RESOURCE_SEPARATOR, "-").trim('-').take(24).ifEmpty { "resource" } + "-${random.nextInt(100, 1_000)}"

    // Fixed date bounds make replay independent of when the generator is run.
    private fun randomInstant(): Instant = Instant.ofEpochSecond(random.nextLong(DATE_START_SECONDS, DATE_END_SECONDS))

    private companion object {
        const val HEX_DIGITS = "0123456789abcdef"
        const val DATE_START_SECONDS = 946_684_800L // 2000-01-01T00:00:00Z
        const val DATE_END_SECONDS = 1_893_456_000L // 2030-01-01T00:00:00Z
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
        val RESOURCE_SEPARATOR = Regex("[^a-z0-9]+")
        val departments = listOf("Engineering", "Product", "Design", "Operations", "Finance", "Marketing", "Sales", "Support")
        val languages = listOf("en" to "English", "ko" to "Korean", "ja" to "Japanese", "zh" to "Chinese", "de" to "German", "fr" to "French", "es" to "Spanish", "pt" to "Portuguese")
        val locales = listOf("en-US", "en-GB", "ko-KR", "ja-JP", "zh-CN", "de-DE", "fr-FR", "es-ES", "pt-BR")
        val timeZones = listOf("UTC", "Asia/Seoul", "Asia/Tokyo", "Asia/Shanghai", "America/New_York", "Europe/London", "Europe/Paris", "Australia/Sydney")
        val currencySymbols = listOf("$", "€", "£", "₩", "¥", "₹")
        val browsers = listOf("Chrome", "Firefox", "Safari", "Edge", "Opera")
        val cloudRegions = listOf("us-east-1", "us-west-2", "eu-west-1", "eu-central-1", "ap-northeast-1", "ap-northeast-2", "ap-southeast-1")
        val httpStatusCodes = listOf(200, 201, 202, 204, 301, 302, 304, 400, 401, 403, 404, 409, 422, 429, 500, 502, 503)
    }
}
