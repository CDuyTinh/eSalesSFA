package com.tinhcd.esalessfa.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: thêm bảng kiểm kê tồn kho và khảo sát trưng bày.
 *
 * Viết migration thay vì bật fallbackToDestructiveMigration, vì destructive sẽ
 * XOÁ SẠCH database — kể cả đơn hàng và lượt ghé chưa kịp đồng bộ của nhân viên
 * đang ở ngoài thị trường. Chỉ thêm bảng mới nên không đụng dữ liệu cũ.
 *
 * Câu SQL phải khớp CHÍNH XÁC schema Room sinh ra (app/schemas/2.json), nếu
 * không Room sẽ ném lỗi ngay khi mở database ở lần chạy đầu sau nâng cấp.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // ── Cấu hình khảo sát ────────────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `survey_types` (
                `id` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL,
                `passScore` REAL NOT NULL, PRIMARY KEY(`id`))
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `survey_question_groups` (
                `id` TEXT NOT NULL, `surveyTypeId` TEXT NOT NULL, `name` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_survey_question_groups_surveyTypeId` " +
                "ON `survey_question_groups` (`surveyTypeId`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `survey_questions` (
                `id` TEXT NOT NULL, `groupId` TEXT NOT NULL, `code` TEXT NOT NULL,
                `content` TEXT NOT NULL, `answerType` TEXT NOT NULL,
                `isRequired` INTEGER NOT NULL, `score` REAL NOT NULL,
                `minPhoto` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL,
                PRIMARY KEY(`id`))
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_survey_questions_groupId` " +
                "ON `survey_questions` (`groupId`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `survey_question_options` (
                `id` TEXT NOT NULL, `questionId` TEXT NOT NULL, `code` TEXT NOT NULL,
                `content` TEXT NOT NULL, `score` REAL NOT NULL,
                `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_survey_question_options_questionId` " +
                "ON `survey_question_options` (`questionId`)"
        )

        // ── Kiểm kê tồn kho ──────────────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `stock_counts` (
                `id` TEXT NOT NULL, `customerId` TEXT NOT NULL,
                `salespersonId` TEXT NOT NULL, `visitId` TEXT,
                `countDate` TEXT NOT NULL, `note` TEXT,
                `syncStatus` TEXT NOT NULL, `syncAttempts` INTEGER NOT NULL,
                `lastError` TEXT, `sessionId` TEXT,
                `clientCreatedAt` INTEGER NOT NULL, `serverAckAt` INTEGER,
                PRIMARY KEY(`id`))
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_counts_salespersonId_countDate` " +
                "ON `stock_counts` (`salespersonId`, `countDate`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_counts_customerId` " +
                "ON `stock_counts` (`customerId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_counts_syncStatus` " +
                "ON `stock_counts` (`syncStatus`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `stock_count_details` (
                `id` TEXT NOT NULL, `stockCountId` TEXT NOT NULL,
                `productId` TEXT NOT NULL, `uomCode` TEXT NOT NULL,
                `qty` REAL NOT NULL, `baseQty` REAL NOT NULL,
                `prevBaseQty` REAL NOT NULL, `suggestQty` REAL NOT NULL,
                PRIMARY KEY(`id`))
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_count_details_stockCountId` " +
                "ON `stock_count_details` (`stockCountId`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_stock_count_details_stockCountId_productId_uomCode` " +
                "ON `stock_count_details` (`stockCountId`, `productId`, `uomCode`)"
        )

        // ── Kết quả khảo sát ─────────────────────────────────────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `surveys` (
                `id` TEXT NOT NULL, `surveyTypeId` TEXT NOT NULL,
                `customerId` TEXT NOT NULL, `salespersonId` TEXT NOT NULL,
                `visitId` TEXT, `surveyDate` TEXT NOT NULL,
                `totalScore` REAL NOT NULL, `isPassed` INTEGER NOT NULL, `note` TEXT,
                `syncStatus` TEXT NOT NULL, `syncAttempts` INTEGER NOT NULL,
                `lastError` TEXT, `sessionId` TEXT,
                `clientCreatedAt` INTEGER NOT NULL, `serverAckAt` INTEGER,
                PRIMARY KEY(`id`))
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_surveys_salespersonId_surveyDate` " +
                "ON `surveys` (`salespersonId`, `surveyDate`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_surveys_customerId` ON `surveys` (`customerId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_surveys_syncStatus` ON `surveys` (`syncStatus`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `survey_answers` (
                `id` TEXT NOT NULL, `surveyId` TEXT NOT NULL,
                `questionId` TEXT NOT NULL, `optionId` TEXT, `answerText` TEXT,
                `answerValue` REAL, `answerBool` INTEGER, `score` REAL NOT NULL,
                PRIMARY KEY(`id`))
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_survey_answers_surveyId` " +
                "ON `survey_answers` (`surveyId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_survey_answers_questionId` " +
                "ON `survey_answers` (`questionId`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `survey_photos` (
                `id` TEXT NOT NULL, `surveyId` TEXT NOT NULL, `questionId` TEXT,
                `localPath` TEXT NOT NULL, `storagePath` TEXT,
                `latitude` REAL, `longitude` REAL,
                `takenAt` INTEGER NOT NULL, `fileSize` INTEGER NOT NULL,
                PRIMARY KEY(`id`))
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_survey_photos_surveyId` " +
                "ON `survey_photos` (`surveyId`)"
        )
    }
}

/**
 * v2 -> v3: nạp lại khách hàng và sản phẩm để sinh lại cột tìm kiếm.
 *
 * Không đổi schema. Trước đây cột nameSearch lấy nguyên từ server, mà server sinh
 * thiếu phần tên riêng nên gõ "minh anh" không ra "Cửa hàng Minh Anh". Giờ client
 * tự sinh bằng SearchText, nhưng các dòng ĐÃ nằm trong máy vẫn giữ giá trị cũ.
 *
 * Xoá mốc version của hai bảng đó trong sync_state là đủ: lượt sync tiếp theo coi
 * chúng như chưa từng tải, kéo lại toàn bộ và mapper sinh lại cột tìm kiếm. Không
 * cần endpoint riêng hay cờ đặc biệt — dùng đúng cơ chế delta sync đang có.
 *
 * Chỉ xoá mốc version, KHÔNG xoá dữ liệu: danh sách khách hàng vẫn dùng được
 * bình thường cho tới khi sync xong.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM `sync_state` WHERE `tableName` IN ('customers', 'products')")
    }
}
