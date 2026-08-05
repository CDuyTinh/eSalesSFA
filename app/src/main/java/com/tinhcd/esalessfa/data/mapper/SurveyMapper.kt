package com.tinhcd.esalessfa.data.mapper

import com.tinhcd.esalessfa.core.database.entity.master.SurveyQuestionEntity
import com.tinhcd.esalessfa.core.database.entity.master.SurveyQuestionGroupEntity
import com.tinhcd.esalessfa.core.database.entity.master.SurveyQuestionOptionEntity
import com.tinhcd.esalessfa.core.database.entity.master.SurveyTypeEntity
import com.tinhcd.esalessfa.core.network.dto.SurveyQuestionDto
import com.tinhcd.esalessfa.core.network.dto.SurveyQuestionGroupDto
import com.tinhcd.esalessfa.core.network.dto.SurveyQuestionOptionDto
import com.tinhcd.esalessfa.core.network.dto.SurveyTypeDto

fun SurveyTypeDto.toEntity() = SurveyTypeEntity(id, code, name, passScore)

fun SurveyQuestionGroupDto.toEntity() =
    SurveyQuestionGroupEntity(id, surveyTypeId, name, sortOrder)

fun SurveyQuestionDto.toEntity() = SurveyQuestionEntity(
    id = id,
    groupId = groupId,
    code = code,
    content = content,
    answerType = answerType,
    isRequired = isRequired,
    score = score,
    minPhoto = minPhoto,
    sortOrder = sortOrder,
)

fun SurveyQuestionOptionDto.toEntity() =
    SurveyQuestionOptionEntity(id, questionId, code, content, score, sortOrder)
