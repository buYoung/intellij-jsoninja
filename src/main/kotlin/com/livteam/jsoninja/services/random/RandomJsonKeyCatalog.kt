package com.livteam.jsoninja.services.random

/** Curated key names only. Value providers and random state never populate this catalog. */
internal object RandomJsonKeyCatalog {
    private val aliases = mapOf(
        "givenName" to "firstName", "familyName" to "lastName", "dateOfBirth" to "birthDate",
        "ipAddress" to "ipv4", "hardwareAddress" to "macAddress", "host" to "hostname",
        "pageNumber" to "page", "currentPage" to "page", "perPage" to "pageSize",
        "itemsPerPage" to "pageSize", "resultsPerPage" to "pageSize",
        "widthPixels" to "imageWidthPixels", "heightPixels" to "imageHeightPixels",
    )

    val entries: List<RandomJsonKeyDefinition> = buildList {

        // IDENTIFIER
        fields(RandomJsonKeyCategory.IDENTIFIER, RandomJsonValueRule.Generated(RandomJsonValueKind.UNIQUE_ID), """
            id
        """)
        fields(RandomJsonKeyCategory.IDENTIFIER, RandomJsonValueRule.IntegerRange(1L, 1000000000L), """
            userId productId orderId recordId accountId
            customerId memberId employeeId organizationId companyId
            departmentId teamId projectId workspaceId tenantId
            subscriptionId invoiceId paymentId shipmentId warehouseId
            supplierId vendorId partnerId contactId addressId
            categoryId parentId ownerId authorId editorId
            reviewerId approverId managerId assigneeId agentId
            deviceId assetId resourceId documentId fileId
            folderId messageId notificationId eventId jobId
            taskId queueId batchId campaignId inventoryId
            variantId skuId lineItemId
        """)
        fields(RandomJsonKeyCategory.IDENTIFIER, RandomJsonValueRule.Generated(RandomJsonValueKind.UUID), """
            sessionId requestId correlationId transactionId
        """)
        fields(RandomJsonKeyCategory.IDENTIFIER, RandomJsonValueRule.Generated(RandomJsonValueKind.HEX_32), """
            traceId
        """)
        fields(RandomJsonKeyCategory.IDENTIFIER, RandomJsonValueRule.Generated(RandomJsonValueKind.HEX_16), """
            spanId
        """)

        // REFERENCE
        fields(RandomJsonKeyCategory.REFERENCE, RandomJsonValueRule.Generated(RandomJsonValueKind.REFERENCE_CODE), """
            orderNumber invoiceNumber ticketNumber trackingNumber referenceCode
            customerNumber accountNumber employeeNumber purchaseOrderNumber receiptNumber
            quoteNumber contractNumber claimNumber policyNumber reservationNumber
            bookingNumber shipmentNumber batchNumber serialNumber confirmationCode
        """)

        // PERSON_NAME
        fields(RandomJsonKeyCategory.PERSON_NAME, RandomJsonValueRule.Generated(RandomJsonValueKind.FIRST_NAME), """
            firstName givenName middleName
        """)
        fields(RandomJsonKeyCategory.PERSON_NAME, RandomJsonValueRule.Generated(RandomJsonValueKind.LAST_NAME), """
            lastName familyName
        """)
        fields(RandomJsonKeyCategory.PERSON_NAME, RandomJsonValueRule.Generated(RandomJsonValueKind.NAME), """
            name fullName preferredName legalName displayName
            authorName ownerName customerName contactName recipientName
            senderName approverName reviewerName managerName employeeName
        """)

        // ACCOUNT
        fields(RandomJsonKeyCategory.ACCOUNT, RandomJsonValueRule.Generated(RandomJsonValueKind.USERNAME), """
            username nickname handle loginName screenName
            alias accountName userHandle publicHandle profileName
            displayHandle ownerUsername authorUsername recipientUsername senderUsername
            adminUsername operatorUsername serviceAccountName botUsername externalUsername
        """)

        // EMAIL
        fields(RandomJsonKeyCategory.EMAIL, RandomJsonValueRule.Generated(RandomJsonValueKind.EMAIL), """
            email contactEmail billingEmail supportEmail notificationEmail
            primaryEmail secondaryEmail workEmail personalEmail recoveryEmail
            senderEmail recipientEmail replyToEmail administratorEmail ownerEmail
            customerEmail salesEmail invoiceEmail securityEmail alternateEmail
        """)

        // PHONE
        fields(RandomJsonKeyCategory.PHONE, RandomJsonValueRule.Generated(RandomJsonValueKind.PHONE), """
            phone mobile officePhone faxNumber contactNumber
            primaryPhone secondaryPhone workPhone homePhone emergencyPhone
            supportPhone billingPhone shippingPhone recipientPhone senderPhone
            customerPhone businessPhone alternatePhone tollFreePhone callbackPhone
        """)

        // URL
        fields(RandomJsonKeyCategory.URL, RandomJsonValueRule.Generated(RandomJsonValueKind.URL), """
            url website homepage callbackUrl redirectUrl
            endpointUrl apiUrl documentationUrl supportUrl helpUrl
            termsUrl privacyUrl loginUrl logoutUrl profileUrl
            productUrl checkoutUrl paymentUrl invoiceUrl trackingUrl
            downloadUrl uploadUrl repositoryUrl issueUrl dashboardUrl
            portalUrl webhookUrl unsubscribeUrl verificationUrl resetUrl
        """)

        // SHORT_TEXT
        fields(RandomJsonKeyCategory.SHORT_TEXT, RandomJsonValueRule.Generated(RandomJsonValueKind.SHORT_TEXT), """
            title subject headline caption summary
            subtitle tagline heading shortDescription previewText
            excerpt tooltip buttonText linkText announcementTitle
            notificationTitle emailSubject pageTitle documentTitle taskTitle
        """)

        // LONG_TEXT
        fields(RandomJsonKeyCategory.LONG_TEXT, RandomJsonValueRule.Generated(RandomJsonValueKind.LONG_TEXT), """
            description content body notes biography
            instructions documentation remarks explanation rationale
            details comment feedback introduction conclusion
            abstract changelog releaseNotes helpText errorDetails
        """)

        // LABEL
        fields(RandomJsonKeyCategory.LABEL, RandomJsonValueRule.Generated(RandomJsonValueKind.TEXT), """
            category label tag segment classification
            type kind group family topic
            theme genre channel sourceType targetType
            resourceType entityType contentCategory productCategory audienceSegment
        """)

        // BOOLEAN
        fields(RandomJsonKeyCategory.BOOLEAN, RandomJsonValueRule.Generated(RandomJsonValueKind.BOOLEAN), """
            isActive isEnabled isVerified hasAccess canEdit
            isDeleted isArchived isPublished isFeatured isDefault
            isPublic isPrivate isRequired isOptional isAvailable
            isVisible isLocked isReadOnly isComplete isApproved
            isPending hasAttachment hasChildren hasErrors hasWarnings
            canRead canWrite canDelete canShare shouldNotify
        """)

        // STATUS
        fields(RandomJsonKeyCategory.STATUS, RandomJsonValueRule.Choice(listOf("pending", "active", "inactive", "suspended", "deleted")), """
            status state accountStatus userStatus membershipStatus
        """)
        fields(RandomJsonKeyCategory.STATUS, RandomJsonValueRule.Choice(listOf("draft", "submitted", "approved", "rejected", "cancelled")), """
            approvalStatus reviewStatus moderationStatus
        """)
        fields(RandomJsonKeyCategory.STATUS, RandomJsonValueRule.Choice(listOf("queued", "running", "succeeded", "failed", "cancelled")), """
            processingStatus jobStatus taskStatus syncStatus importStatus
            exportStatus
        """)
        fields(RandomJsonKeyCategory.STATUS, RandomJsonValueRule.Choice(listOf("pending", "authorized", "paid", "refunded", "failed")), """
            paymentStatus billingStatus
        """)
        fields(RandomJsonKeyCategory.STATUS, RandomJsonValueRule.Choice(listOf("available", "unavailable", "reserved", "discontinued")), """
            availability inventoryStatus
        """)
        fields(RandomJsonKeyCategory.STATUS, RandomJsonValueRule.Choice(listOf("pending", "shipped", "delivered", "returned", "cancelled")), """
            orderStatus deliveryStatus
        """)

        // ROLE
        fields(RandomJsonKeyCategory.ROLE, RandomJsonValueRule.Choice(listOf("viewer", "editor", "moderator", "administrator", "owner")), """
            role userRole memberRole projectRole workspaceRole
            accessLevel
        """)
        fields(RandomJsonKeyCategory.ROLE, RandomJsonValueRule.Choice(listOf("read", "write", "update", "delete", "manage")), """
            permission requiredPermission grantedPermission resourcePermission actionPermission
        """)
        fields(RandomJsonKeyCategory.ROLE, RandomJsonValueRule.Choice(listOf("profile", "orders", "products", "billing", "settings")), """
            scope accessScope authorizationScope resourceScope
        """)
        fields(RandomJsonKeyCategory.ROLE, RandomJsonValueRule.Choice(listOf("user", "service", "group", "application")), """
            principalType actorType identityType
        """)
        fields(RandomJsonKeyCategory.ROLE, RandomJsonValueRule.Choice(listOf("allow", "deny", "inherit")), """
            permissionEffect accessPolicy
        """)

        // COUNT
        fields(RandomJsonKeyCategory.COUNT, RandomJsonValueRule.IntegerRange(0L, 1000000L), """
            count quantity stock itemCount viewCount
            clickCount likeCount shareCount commentCount downloadCount
            uploadCount memberCount userCount employeeCount orderCount
            productCount recordCount rowCount columnCount messageCount
            notificationCount errorCount warningCount failureCount successCount
            attemptCount childCount attachmentCount followerCount subscriberCount
        """)

        // PAGINATION
        fields(RandomJsonKeyCategory.PAGINATION, RandomJsonValueRule.IntegerRange(1L, 100L), """
            page pageNumber currentPage firstPage lastPage
            pageSize perPage limit resultsPerPage itemsPerPage
        """)
        fields(RandomJsonKeyCategory.PAGINATION, RandomJsonValueRule.IntegerRange(0L, 10000L), """
            offset totalPages totalCount totalResults totalItems
            startIndex endIndex nextOffset previousOffset resultCount
        """)

        // MONEY
        fields(RandomJsonKeyCategory.MONEY, RandomJsonValueRule.DecimalRange(0L, 10000000L, 2), """
            price amount balance subtotal shippingFee
            totalAmount unitPrice listPrice salePrice purchasePrice
            cost unitCost netAmount grossAmount taxAmount
            discountAmount refundAmount paidAmount dueAmount creditAmount
            debitAmount depositAmount fee serviceFee handlingFee
            insuranceAmount tipAmount commissionAmount budget monthlyPrice
        """)

        // CURRENCY
        fields(RandomJsonKeyCategory.CURRENCY, RandomJsonValueRule.Generated(RandomJsonValueKind.CURRENCY_CODE), """
            currency currencyCode billingCurrency paymentCurrency settlementCurrency
            baseCurrency quoteCurrency sourceCurrency targetCurrency accountCurrency
            invoiceCurrency displayCurrency reportingCurrency localCurrency preferredCurrency
        """)
        fields(RandomJsonKeyCategory.CURRENCY, RandomJsonValueRule.Generated(RandomJsonValueKind.CURRENCY_NAME), """
            currencyName baseCurrencyName settlementCurrencyName
        """)
        fields(RandomJsonKeyCategory.CURRENCY, RandomJsonValueRule.Generated(RandomJsonValueKind.CURRENCY_SYMBOL), """
            currencySymbol displayCurrencySymbol
        """)

        // RATIO
        fields(RandomJsonKeyCategory.RATIO, RandomJsonValueRule.DecimalRange(0L, 10000L, 4), """
            ratio probability successRatio errorRatio conversionRatio
            occupancyRatio confidenceRatio samplingRatio utilizationRatio retentionRatio
        """)
        fields(RandomJsonKeyCategory.RATIO, RandomJsonValueRule.DecimalRange(0L, 10000L, 2), """
            percentage discountRate completionRate taxRate interestRate
            growthRate churnRate acceptanceRate failureRate progressPercent
        """)

        // SCORE
        fields(RandomJsonKeyCategory.SCORE, RandomJsonValueRule.DecimalRange(0L, 50L, 1), """
            rating reviewRating productRating serviceRating averageRating
        """)
        fields(RandomJsonKeyCategory.SCORE, RandomJsonValueRule.DecimalRange(0L, 1000L, 1), """
            reviewScore qualityScore relevanceScore satisfactionScore trustScore
        """)
        fields(RandomJsonKeyCategory.SCORE, RandomJsonValueRule.IntegerRange(0L, 100L), """
            score reputation riskScore confidenceScore performanceScore
            popularityScore healthScore priorityScore
        """)
        fields(RandomJsonKeyCategory.SCORE, RandomJsonValueRule.IntegerRange(1L, 1000L), """
            rank positionRank
        """)

        // MEASUREMENT
        fields(RandomJsonKeyCategory.MEASUREMENT, RandomJsonValueRule.DecimalRange(0L, 100000L, 2), """
            weightKg heightCm widthCm lengthCm depthCm
            distanceKm volumeMl areaSquareMeters capacityLiters pressureKpa
        """)
        fields(RandomJsonKeyCategory.MEASUREMENT, RandomJsonValueRule.DecimalRange(-5000L, 6000L, 2), """
            temperatureC ambientTemperatureC minimumTemperatureC maximumTemperatureC
        """)
        fields(RandomJsonKeyCategory.MEASUREMENT, RandomJsonValueRule.DecimalRange(0L, 100000L, 2), """
            speedKph powerWatts voltageVolts currentAmps energyKwh
            frequencyHz
        """)

        // DATE
        fields(RandomJsonKeyCategory.DATE, RandomJsonValueRule.Generated(RandomJsonValueKind.BIRTH_DATE), """
            birthDate dateOfBirth
        """)
        fields(RandomJsonKeyCategory.DATE, RandomJsonValueRule.Generated(RandomJsonValueKind.DATE), """
            startDate endDate dueDate releaseDate effectiveDate
            expirationDate registrationDate purchaseDate orderDate deliveryDate
            invoiceDate paymentDate renewalDate cancellationDate scheduledDate
            completedDate reviewedDate archivedDate
        """)

        // DATE_TIME
        fields(RandomJsonKeyCategory.DATE_TIME, RandomJsonValueRule.Generated(RandomJsonValueKind.DATE_TIME), """
            createdAt updatedAt deletedAt expiresAt publishedAt
            startedAt finishedAt completedAt scheduledAt processedAt
            submittedAt approvedAt rejectedAt cancelledAt archivedAt
            restoredAt lastLoginAt lastSeenAt lastAccessedAt lastModifiedAt
            receivedAt sentAt deliveredAt readAt verifiedAt
            activatedAt deactivatedAt suspendedAt resumedAt synchronizedAt
        """)

        // TIME
        fields(RandomJsonKeyCategory.TIME, RandomJsonValueRule.Generated(RandomJsonValueKind.TIME), """
            openingTime closingTime startTime endTime scheduledTime
            departureTime arrivalTime pickupTime deliveryTime checkInTime
            checkOutTime reminderTime alarmTime executionTime cutoffTime
            lunchStartTime lunchEndTime breakStartTime breakEndTime maintenanceTime
        """)

        // EPOCH
        fields(RandomJsonKeyCategory.EPOCH, RandomJsonValueRule.Generated(RandomJsonValueKind.EPOCH_SECONDS), """
            epochSeconds createdAtSeconds updatedAtSeconds expiresAtSeconds eventTimeSeconds
            startTimeSeconds endTimeSeconds receivedAtSeconds sentAtSeconds processedAtSeconds
        """)
        fields(RandomJsonKeyCategory.EPOCH, RandomJsonValueRule.Generated(RandomJsonValueKind.EPOCH_MILLIS), """
            epochMillis createdAtMillis updatedAtMillis expiresAtMillis eventTimeMillis
            startTimeMillis endTimeMillis receivedAtMillis sentAtMillis processedAtMillis
        """)

        // DURATION
        fields(RandomJsonKeyCategory.DURATION, RandomJsonValueRule.IntegerRange(1L, 120000L), """
            durationMs timeoutMs latencyMs retryDelayMs processingTimeMs
            responseTimeMs executionTimeMs elapsedTimeMs waitTimeMs intervalMs
            debounceMs throttleMs
        """)
        fields(RandomJsonKeyCategory.DURATION, RandomJsonValueRule.IntegerRange(1L, 86400L), """
            ttlSeconds durationSeconds timeoutSeconds retryDelaySeconds intervalSeconds
            retentionSeconds cooldownSeconds sessionLifetimeSeconds
        """)

        // ORGANIZATION
        fields(RandomJsonKeyCategory.ORGANIZATION, RandomJsonValueRule.Generated(RandomJsonValueKind.COMPANY), """
            companyName organizationName employerName businessName legalEntityName
            tradingName subsidiaryName parentCompanyName vendorName supplierName
            partnerName clientCompanyName agencyName institutionName foundationName
            associationName
        """)
        fields(RandomJsonKeyCategory.ORGANIZATION, RandomJsonValueRule.Generated(RandomJsonValueKind.DEPARTMENT), """
            departmentName teamName divisionName businessUnitName
        """)

        // JOB
        fields(RandomJsonKeyCategory.JOB, RandomJsonValueRule.Generated(RandomJsonValueKind.JOB_TITLE), """
            jobTitle occupation profession positionTitle primaryOccupation
            secondaryOccupation currentJobTitle previousJobTitle managerJobTitle desiredJobTitle
        """)
        fields(RandomJsonKeyCategory.JOB, RandomJsonValueRule.Choice(listOf("intern", "junior", "mid", "senior", "lead", "principal", "executive")), """
            seniorityLevel jobLevel careerLevel positionLevel
        """)
        fields(RandomJsonKeyCategory.JOB, RandomJsonValueRule.Choice(listOf("fullTime", "partTime", "contract", "temporary", "freelance")), """
            employmentType contractType engagementType
        """)
        fields(RandomJsonKeyCategory.JOB, RandomJsonValueRule.Choice(listOf("engineering", "design", "operations", "marketing", "finance")), """
            jobFamily careerField specialization
        """)

        // ADDRESS
        fields(RandomJsonKeyCategory.ADDRESS, RandomJsonValueRule.Generated(RandomJsonValueKind.STREET_ADDRESS), """
            streetAddress addressLine1 mailingAddress billingAddress shippingAddress
            deliveryAddress registeredAddress residentialAddress
        """)
        fields(RandomJsonKeyCategory.ADDRESS, RandomJsonValueRule.Generated(RandomJsonValueKind.STREET_NAME), """
            streetName roadName
        """)
        fields(RandomJsonKeyCategory.ADDRESS, RandomJsonValueRule.Generated(RandomJsonValueKind.CITY), """
            city town district
        """)
        fields(RandomJsonKeyCategory.ADDRESS, RandomJsonValueRule.Generated(RandomJsonValueKind.STATE), """
            region stateName province
        """)
        fields(RandomJsonKeyCategory.ADDRESS, RandomJsonValueRule.Generated(RandomJsonValueKind.COUNTRY), """
            country
        """)
        fields(RandomJsonKeyCategory.ADDRESS, RandomJsonValueRule.Generated(RandomJsonValueKind.POSTAL_CODE), """
            postalCode zipCode
        """)
        fields(RandomJsonKeyCategory.ADDRESS, RandomJsonValueRule.Generated(RandomJsonValueKind.BUILDING_NUMBER), """
            buildingNumber
        """)

        // GEO
        fields(RandomJsonKeyCategory.GEO, RandomJsonValueRule.DecimalRange(-900000L, 900000L, 4), """
            latitude originLatitude destinationLatitude centerLatitude northLatitude
            southLatitude
        """)
        fields(RandomJsonKeyCategory.GEO, RandomJsonValueRule.DecimalRange(-1800000L, 1800000L, 4), """
            longitude originLongitude destinationLongitude centerLongitude eastLongitude
            westLongitude
        """)
        fields(RandomJsonKeyCategory.GEO, RandomJsonValueRule.DecimalRange(-50000L, 900000L, 2), """
            altitudeMeters elevationMeters
        """)
        fields(RandomJsonKeyCategory.GEO, RandomJsonValueRule.DecimalRange(0L, 100000L, 2), """
            accuracyMeters radiusMeters
        """)
        fields(RandomJsonKeyCategory.GEO, RandomJsonValueRule.Generated(RandomJsonValueKind.GEOHASH), """
            geohash originGeohash destinationGeohash
        """)
        fields(RandomJsonKeyCategory.GEO, RandomJsonValueRule.Generated(RandomJsonValueKind.CITY), """
            locationName
        """)

        // LOCALE
        fields(RandomJsonKeyCategory.LOCALE, RandomJsonValueRule.Generated(RandomJsonValueKind.LANGUAGE_NAME), """
            language preferredLanguage nativeLanguage
        """)
        fields(RandomJsonKeyCategory.LOCALE, RandomJsonValueRule.Generated(RandomJsonValueKind.LANGUAGE_CODE), """
            languageCode contentLanguageCode interfaceLanguageCode
        """)
        fields(RandomJsonKeyCategory.LOCALE, RandomJsonValueRule.Generated(RandomJsonValueKind.LOCALE), """
            locale preferredLocale defaultLocale displayLocale contentLocale
            formattingLocale
        """)
        fields(RandomJsonKeyCategory.LOCALE, RandomJsonValueRule.Generated(RandomJsonValueKind.TIME_ZONE), """
            timeZone userTimeZone serverTimeZone businessTimeZone
        """)
        fields(RandomJsonKeyCategory.LOCALE, RandomJsonValueRule.Generated(RandomJsonValueKind.COUNTRY_CODE), """
            countryCode billingCountryCode shippingCountryCode residenceCountryCode
        """)

        // PRODUCT
        fields(RandomJsonKeyCategory.PRODUCT, RandomJsonValueRule.Generated(RandomJsonValueKind.PRODUCT_NAME), """
            productName itemName modelName variantName collectionName
            bundleName packageName catalogName
        """)
        fields(RandomJsonKeyCategory.PRODUCT, RandomJsonValueRule.Generated(RandomJsonValueKind.BRAND), """
            brand brandName manufacturerBrand designerBrand
        """)
        fields(RandomJsonKeyCategory.PRODUCT, RandomJsonValueRule.Generated(RandomJsonValueKind.MATERIAL), """
            material primaryMaterial secondaryMaterial fabricType
        """)
        fields(RandomJsonKeyCategory.PRODUCT, RandomJsonValueRule.Generated(RandomJsonValueKind.SKU), """
            sku productCode variantCode barcode
        """)

        // COLOR
        fields(RandomJsonKeyCategory.COLOR, RandomJsonValueRule.Generated(RandomJsonValueKind.COLOR_NAME), """
            color colorName primaryColor secondaryColor dominantColor
            materialColor
        """)
        fields(RandomJsonKeyCategory.COLOR, RandomJsonValueRule.Generated(RandomJsonValueKind.HEX_COLOR), """
            hexColor backgroundColor accentColor foregroundColor borderColor
            textColor linkColor buttonColor highlightColor shadowColor
            fillColor strokeColor themeColor selectionColor
        """)

        // FILE
        fields(RandomJsonKeyCategory.FILE, RandomJsonValueRule.Generated(RandomJsonValueKind.FILE_NAME), """
            fileName originalFileName storedFileName attachmentName documentName
            archiveName
        """)
        fields(RandomJsonKeyCategory.FILE, RandomJsonValueRule.Generated(RandomJsonValueKind.FILE_EXTENSION), """
            fileExtension extension archiveExtension
        """)
        fields(RandomJsonKeyCategory.FILE, RandomJsonValueRule.Generated(RandomJsonValueKind.MIME_TYPE), """
            mimeType fileMimeType attachmentMimeType
        """)
        fields(RandomJsonKeyCategory.FILE, RandomJsonValueRule.IntegerRange(0L, 100000000L), """
            fileSizeBytes compressedSizeBytes uncompressedSizeBytes attachmentSizeBytes
        """)
        fields(RandomJsonKeyCategory.FILE, RandomJsonValueRule.Generated(RandomJsonValueKind.FILE_PATH), """
            filePath relativePath absolutePath directoryPath
        """)

        // MEDIA
        fields(RandomJsonKeyCategory.MEDIA, RandomJsonValueRule.Generated(RandomJsonValueKind.IMAGE_URL), """
            imageUrl avatarUrl thumbnailUrl coverUrl bannerUrl
            logoUrl previewUrl posterUrl iconUrl
        """)
        fields(RandomJsonKeyCategory.MEDIA, RandomJsonValueRule.IntegerRange(1L, 4096L), """
            widthPixels heightPixels thumbnailWidthPixels thumbnailHeightPixels imageWidthPixels
            imageHeightPixels
        """)
        fields(RandomJsonKeyCategory.MEDIA, RandomJsonValueRule.IntegerRange(1L, 240L), """
            frameRate
        """)
        fields(RandomJsonKeyCategory.MEDIA, RandomJsonValueRule.IntegerRange(8000L, 192000L), """
            sampleRateHz
        """)
        fields(RandomJsonKeyCategory.MEDIA, RandomJsonValueRule.IntegerRange(1L, 10000L), """
            bitrateKbps
        """)
        fields(RandomJsonKeyCategory.MEDIA, RandomJsonValueRule.DecimalRange(0L, 360000L, 2), """
            mediaDurationSeconds
        """)
        fields(RandomJsonKeyCategory.MEDIA, RandomJsonValueRule.Choice(listOf("landscape", "portrait", "square")), """
            orientation
        """)

        // NETWORK
        fields(RandomJsonKeyCategory.NETWORK, RandomJsonValueRule.Generated(RandomJsonValueKind.IPV4), """
            ipv4 ipAddress clientIp serverIp remoteIp
            localIp sourceIp destinationIp gatewayIp subnetAddress
        """)
        fields(RandomJsonKeyCategory.NETWORK, RandomJsonValueRule.Generated(RandomJsonValueKind.IPV6), """
            ipv6 clientIpv6 serverIpv6
        """)
        fields(RandomJsonKeyCategory.NETWORK, RandomJsonValueRule.Generated(RandomJsonValueKind.MAC_ADDRESS), """
            macAddress hardwareAddress
        """)
        fields(RandomJsonKeyCategory.NETWORK, RandomJsonValueRule.Generated(RandomJsonValueKind.HOSTNAME), """
            hostname domainName host
        """)
        fields(RandomJsonKeyCategory.NETWORK, RandomJsonValueRule.IntegerRange(1L, 65535L), """
            port remotePort
        """)

        // HTTP
        fields(RandomJsonKeyCategory.HTTP, RandomJsonValueRule.Choice(listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")), """
            httpMethod requestMethod
        """)
        fields(RandomJsonKeyCategory.HTTP, RandomJsonValueRule.Choice(listOf("http", "https")), """
            protocol scheme
        """)
        fields(RandomJsonKeyCategory.HTTP, RandomJsonValueRule.Choice(listOf("1.1", "2", "3")), """
            httpVersion
        """)
        fields(RandomJsonKeyCategory.HTTP, RandomJsonValueRule.Choice(listOf("keep-alive", "close")), """
            connectionType
        """)
        fields(RandomJsonKeyCategory.HTTP, RandomJsonValueRule.Choice(listOf("gzip", "br", "identity")), """
            contentEncoding acceptEncoding
        """)
        fields(RandomJsonKeyCategory.HTTP, RandomJsonValueRule.Choice(listOf("no-cache", "no-store", "public", "private")), """
            cacheControl
        """)
        fields(RandomJsonKeyCategory.HTTP, RandomJsonValueRule.Choice(listOf("utf-8", "iso-8859-1")), """
            charset
        """)
        fields(RandomJsonKeyCategory.HTTP, RandomJsonValueRule.Choice(listOf("cors", "no-cors", "same-origin", "navigate")), """
            requestMode
        """)
        fields(RandomJsonKeyCategory.HTTP, RandomJsonValueRule.Choice(listOf("omit", "same-origin", "include")), """
            credentialsMode
        """)
        fields(RandomJsonKeyCategory.HTTP, RandomJsonValueRule.Choice(listOf("application/json", "text/plain", "text/html", "application/xml")), """
            contentType acceptType
        """)
        fields(RandomJsonKeyCategory.HTTP, RandomJsonValueRule.Generated(RandomJsonValueKind.HTTP_PATH), """
            requestPath routePath endpointPath
        """)
        fields(RandomJsonKeyCategory.HTTP, RandomJsonValueRule.Generated(RandomJsonValueKind.HTTP_STATUS), """
            statusCode responseStatusCode upstreamStatusCode
        """)

        // DEVICE
        fields(RandomJsonKeyCategory.DEVICE, RandomJsonValueRule.Generated(RandomJsonValueKind.DEVICE_MODEL), """
            deviceName deviceModel modelNumber hardwareModel
        """)
        fields(RandomJsonKeyCategory.DEVICE, RandomJsonValueRule.Generated(RandomJsonValueKind.MANUFACTURER), """
            manufacturer deviceManufacturer hardwareVendor
        """)
        fields(RandomJsonKeyCategory.DEVICE, RandomJsonValueRule.Generated(RandomJsonValueKind.OPERATING_SYSTEM), """
            operatingSystem osName platformName
        """)
        fields(RandomJsonKeyCategory.DEVICE, RandomJsonValueRule.Generated(RandomJsonValueKind.BROWSER), """
            browserName defaultBrowser
        """)
        fields(RandomJsonKeyCategory.DEVICE, RandomJsonValueRule.Generated(RandomJsonValueKind.USER_AGENT), """
            userAgent browserUserAgent
        """)
        fields(RandomJsonKeyCategory.DEVICE, RandomJsonValueRule.Choice(listOf("desktop", "laptop", "tablet", "phone", "server", "wearable")), """
            deviceType formFactor
        """)
        fields(RandomJsonKeyCategory.DEVICE, RandomJsonValueRule.Choice(listOf("x86_64", "arm64", "x86", "arm")), """
            architecture cpuArchitecture
        """)
        fields(RandomJsonKeyCategory.DEVICE, RandomJsonValueRule.IntegerRange(1L, 32L), """
            cpuCoreCount
        """)
        fields(RandomJsonKeyCategory.DEVICE, RandomJsonValueRule.IntegerRange(1L, 128L), """
            memoryGb
        """)

        // SOFTWARE
        fields(RandomJsonKeyCategory.SOFTWARE, RandomJsonValueRule.Generated(RandomJsonValueKind.APP_NAME), """
            appName applicationName softwareName packageDisplayName clientName
        """)
        fields(RandomJsonKeyCategory.SOFTWARE, RandomJsonValueRule.Generated(RandomJsonValueKind.VERSION), """
            appVersion apiVersion softwareVersion clientVersion serverVersion
            sdkVersion packageVersion schemaVersion minimumVersion maximumVersion
        """)
        fields(RandomJsonKeyCategory.SOFTWARE, RandomJsonValueRule.IntegerRange(1L, 100000L), """
            buildNumber revisionNumber
        """)
        fields(RandomJsonKeyCategory.SOFTWARE, RandomJsonValueRule.Choice(listOf("stable", "beta", "alpha", "nightly")), """
            releaseChannel updateChannel
        """)
        fields(RandomJsonKeyCategory.SOFTWARE, RandomJsonValueRule.Generated(RandomJsonValueKind.HEX_40), """
            commitHash
        """)

        // CLOUD
        fields(RandomJsonKeyCategory.CLOUD, RandomJsonValueRule.Generated(RandomJsonValueKind.CLOUD_REGION), """
            regionCode cloudRegion deploymentRegion primaryRegion secondaryRegion
        """)
        fields(RandomJsonKeyCategory.CLOUD, RandomJsonValueRule.Choice(listOf("small", "medium", "large", "xlarge")), """
            instanceType machineType nodeSize
        """)
        fields(RandomJsonKeyCategory.CLOUD, RandomJsonValueRule.Generated(RandomJsonValueKind.CLOUD_RESOURCE), """
            bucketName containerName clusterName namespaceName podName
            instanceName virtualMachineName storageAccountName resourceGroupName availabilityZone
        """)
        fields(RandomJsonKeyCategory.CLOUD, RandomJsonValueRule.Choice(listOf("aws", "azure", "gcp", "local")), """
            cloudProvider infrastructureProvider
        """)

        // LOG
        fields(RandomJsonKeyCategory.LOG, RandomJsonValueRule.Choice(listOf("trace", "debug", "info", "warn", "error", "fatal")), """
            logLevel severity eventSeverity minimumLogLevel
        """)
        fields(RandomJsonKeyCategory.LOG, RandomJsonValueRule.Generated(RandomJsonValueKind.REFERENCE_CODE), """
            eventName eventType eventCode auditCode actionName
            operationName
        """)
        fields(RandomJsonKeyCategory.LOG, RandomJsonValueRule.Generated(RandomJsonValueKind.SERVICE_NAME), """
            source serviceName componentName moduleName loggerName
            applicationSource
        """)
        fields(RandomJsonKeyCategory.LOG, RandomJsonValueRule.Generated(RandomJsonValueKind.SHORT_TEXT), """
            message logMessage eventMessage auditMessage
        """)

        // TASK
        fields(RandomJsonKeyCategory.TASK, RandomJsonValueRule.Generated(RandomJsonValueKind.REFERENCE_CODE), """
            jobName taskName queueName workerName schedulerName
            workflowName pipelineName stageName stepName
        """)
        fields(RandomJsonKeyCategory.TASK, RandomJsonValueRule.IntegerRange(0L, 10L), """
            retryCount maxRetries attemptNumber priority taskPriority
            queuePriority
        """)
        fields(RandomJsonKeyCategory.TASK, RandomJsonValueRule.IntegerRange(1L, 100L), """
            concurrency workerCount batchSize parallelism
        """)
        fields(RandomJsonKeyCategory.TASK, RandomJsonValueRule.Choice(listOf("manual", "scheduled", "event", "retry")), """
            triggerType
        """)

        // HASH
        fields(RandomJsonKeyCategory.HASH, RandomJsonValueRule.Generated(RandomJsonValueKind.HEX_16), """
            shortHash hashPrefix
        """)
        fields(RandomJsonKeyCategory.HASH, RandomJsonValueRule.Generated(RandomJsonValueKind.HEX_32), """
            md5 checksum contentChecksum fileChecksum
        """)
        fields(RandomJsonKeyCategory.HASH, RandomJsonValueRule.Generated(RandomJsonValueKind.HEX_40), """
            sha1 revisionHash
        """)
        fields(RandomJsonKeyCategory.HASH, RandomJsonValueRule.Generated(RandomJsonValueKind.HEX_64), """
            sha256 digest fingerprint contentHash fileHash
            payloadHash requestHash responseHash signatureHash integrityHash
        """)
        fields(RandomJsonKeyCategory.HASH, RandomJsonValueRule.Generated(RandomJsonValueKind.HEX_128), """
            sha512 extendedDigest
        """)

        // OBJECT
        fields(RandomJsonKeyCategory.OBJECT, null, """
            profile contact preferences accountDetails
        """, RandomJsonDomain.ACCOUNT)
        fields(RandomJsonKeyCategory.OBJECT, null, """
            organization companyDetails teamDetails
        """, RandomJsonDomain.ORGANIZATION)
        fields(RandomJsonKeyCategory.OBJECT, null, """
            productDetails pricing inventory
        """, RandomJsonDomain.PRODUCT)
        fields(RandomJsonKeyCategory.OBJECT, null, """
            shipping destination origin
        """, RandomJsonDomain.SHIPPING)
        fields(RandomJsonKeyCategory.OBJECT, null, """
            metadata settings payload context configuration
            options attributes
        """)

        // VALUE_ARRAY
        fields(RandomJsonKeyCategory.VALUE_ARRAY, RandomJsonValueRule.Generated(RandomJsonValueKind.TEXT), """
            tags labels keywords categories topics
            searchTerms
        """)
        fields(RandomJsonKeyCategory.VALUE_ARRAY, RandomJsonValueRule.Generated(RandomJsonValueKind.LOCALE), """
            supportedLocales availableLocales
        """)
        fields(RandomJsonKeyCategory.VALUE_ARRAY, RandomJsonValueRule.Generated(RandomJsonValueKind.LANGUAGE_CODE), """
            supportedLanguages
        """)
        fields(RandomJsonKeyCategory.VALUE_ARRAY, RandomJsonValueRule.Generated(RandomJsonValueKind.COUNTRY_CODE), """
            allowedCountries
        """)
        fields(RandomJsonKeyCategory.VALUE_ARRAY, RandomJsonValueRule.Generated(RandomJsonValueKind.CURRENCY_CODE), """
            supportedCurrencies
        """)
        fields(RandomJsonKeyCategory.VALUE_ARRAY, RandomJsonValueRule.Generated(RandomJsonValueKind.EMAIL), """
            recipients emailAddresses
        """)
        fields(RandomJsonKeyCategory.VALUE_ARRAY, RandomJsonValueRule.Generated(RandomJsonValueKind.URL), """
            links urls
        """)
        fields(RandomJsonKeyCategory.VALUE_ARRAY, RandomJsonValueRule.IntegerRange(0L, 100L), """
            scores priorities
        """)
        fields(RandomJsonKeyCategory.VALUE_ARRAY, RandomJsonValueRule.Generated(RandomJsonValueKind.COLOR_NAME), """
            colors
        """)
        fields(RandomJsonKeyCategory.VALUE_ARRAY, RandomJsonValueRule.Generated(RandomJsonValueKind.REFERENCE_CODE), """
            codes
        """)
        fields(RandomJsonKeyCategory.VALUE_ARRAY, RandomJsonValueRule.Generated(RandomJsonValueKind.UUID), """
            identifiers
        """)

        // OBJECT_ARRAY
        fields(RandomJsonKeyCategory.OBJECT_ARRAY, null, """
            users members contacts accounts profiles
        """, RandomJsonDomain.ACCOUNT)
        fields(RandomJsonKeyCategory.OBJECT_ARRAY, null, """
            teams organizations departments employees
        """, RandomJsonDomain.ORGANIZATION)
        fields(RandomJsonKeyCategory.OBJECT_ARRAY, null, """
            products variants inventoryItems catalogs
        """, RandomJsonDomain.PRODUCT)
        fields(RandomJsonKeyCategory.OBJECT_ARRAY, null, """
            orders invoices payments transactions
        """, RandomJsonDomain.ORDER)
        fields(RandomJsonKeyCategory.OBJECT_ARRAY, null, """
            shipments deliveries destinations
        """, RandomJsonDomain.SHIPPING)
        fields(RandomJsonKeyCategory.OBJECT_ARRAY, null, """
            articles comments reviews attachments
        """, RandomJsonDomain.CONTENT)
        fields(RandomJsonKeyCategory.OBJECT_ARRAY, null, """
            tasks jobs
        """, RandomJsonDomain.TASK)
        fields(RandomJsonKeyCategory.OBJECT_ARRAY, null, """
            items records results entries
        """)
    }.also { entries ->
        check(entries.size == 1_000)
        check(entries.map { it.key }.toSet().size == entries.size)
        check(entries.map { it.category }.toSet() == RandomJsonKeyCategory.entries.toSet())
    }

    val byCategory: Map<RandomJsonKeyCategory, List<RandomJsonKeyDefinition>> = entries.groupBy { it.category }
    val byKey: Map<String, RandomJsonKeyDefinition> = entries.associateBy { it.key }

    private fun MutableList<RandomJsonKeyDefinition>.fields(
        category: RandomJsonKeyCategory,
        rule: RandomJsonValueRule?,
        names: String,
        childDomain: RandomJsonDomain? = null,
    ) {
        names.trim().split(Regex("\\s+")).forEach { key ->
            add(RandomJsonKeyDefinition(key, category, rule, aliases[key] ?: key, childDomain))
        }
    }
}
