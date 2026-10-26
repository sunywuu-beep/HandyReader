package com.wxn.reader.data.repository

import androidx.room.withTransaction
import com.wxn.base.util.Logger
import com.wxn.reader.data.mapper.sherpamodel.SherpaModelMapper
import com.wxn.reader.data.remote.api.TTSModelsApi
import com.wxn.reader.data.source.local.AppDatabase
import com.wxn.reader.data.source.local.dao.SherpaModelDao
import com.wxn.reader.data.source.local.dao.SherpaSpeakerDao
import com.wxn.reader.domain.model.TTSModelData
import com.wxn.reader.util.tts.data.Speaker
import com.wxn.reader.domain.repository.TTSModelsRepository
import com.wxn.reader.util.download.OKHttpStringStreamer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TTSModelsRepositoryImpl @Inject constructor(
    private val api: TTSModelsApi,
    private val modelDao: SherpaModelDao,
    private val speakerDao: SherpaSpeakerDao,
    private val mapper: SherpaModelMapper,
    private val appDatabase: AppDatabase,
    private val httpStringStreamer: OKHttpStringStreamer
) : TTSModelsRepository {

    override suspend fun getModels(
        page: Int,
        pageSize: Int
    ): Result<Pair<List<TTSModelData>?, Int?>> {
        val result = api.ttsModelsList(page, pageSize).map { response ->
            Pair(response.data?.list, response.pagination?.totalPages)
        }
        if (result.isSuccess && !result.getOrNull()?.first.isNullOrEmpty()) {
            return result
        }
        if (page == 1) {
            val kokoro = createKokoroModelData("kokoro-int8-multi-lang-v1_1")
            return Result.success(Pair(listOf(kokoro), 1))
        }
        return result
    }

    override suspend fun getModelCard(url: String): Result<String> {
        return httpStringStreamer.getStringFromUrl(url)
    }

    override fun getDownloadedModels(): Flow<List<TTSModelData>> {
        return modelDao.getModelsWithSpeakers().map { modelsWithSpeakers ->
            modelsWithSpeakers.mapNotNull { modelWithSpeakers ->
                try {
                    mapper.toTTSModelData(
                        modelWithSpeakers.model,
                        modelWithSpeakers.speakers
                    )
                } catch (e: Exception) {
                    Logger.e("Failed to map model ${modelWithSpeakers.model.name}: ${e.message}")
                    null
                }
            }
        }
    }

    override suspend fun saveModel(model: TTSModelData) {
        try {
            appDatabase.withTransaction {
                val entity = mapper.toSherpaModelEntity(model)
                modelDao.insertModel(entity)

                val speakers = mapper.toSherpaSpeakerEntities(model.name, model.speakers)
                speakerDao.insertSpeakers(speakers)
            }
        } catch (e: Exception) {
            Logger.e("Failed to save model ${model.name}: ${e.message}")
            throw e
        }
    }

    override suspend fun isDownloaded(modelId: String): Boolean {
        return modelDao.getModelByName(modelId) != null
    }

    override suspend fun getLocalModelByName(modelId: String): TTSModelData? {
        val entry = modelDao.getModelByName(modelId)
        if (entry != null) {
            val speakers = speakerDao.getSpeakersByModel(modelId).firstOrNull() ?: emptyList()
            return mapper.toTTSModelData(entry, speakers)
        }
        if (modelId == "kokoro-int8-multi-lang-v1_1" || modelId.startsWith("kokoro")) {
            val kokoroModel = createKokoroModelData(modelId)
            try {
                saveModel(kokoroModel)
            } catch (e: Exception) {
                Logger.e("Failed to auto-save Kokoro model: ${e.message}")
            }
            return kokoroModel
        }
        return null
    }

    private fun createKokoroModelData(modelId: String): TTSModelData {
        val speakers = mutableListOf<Speaker>()
        speakers.add(Speaker(id = "${modelId}_af_alloy", index = 0, name = "af_alloy (美音女)", gender = "female", locale = "en-US", active = true, sampleVoice = ""))
        speakers.add(Speaker(id = "${modelId}_af_aoede", index = 1, name = "af_aoede (美音女)", gender = "female", locale = "en-US", active = false, sampleVoice = ""))
        speakers.add(Speaker(id = "${modelId}_bf_alice", index = 2, name = "bf_alice (英音女)", gender = "female", locale = "en-GB", active = false, sampleVoice = ""))
        for (i in 3..57) {
            val num = String.format("%03d", i - 2)
            speakers.add(Speaker(id = "${modelId}_zf_$num", index = i, name = "zf_$num (国语女)", gender = "female", locale = "zh-CN", active = false, sampleVoice = ""))
        }
        for (i in 58..102) {
            val num = String.format("%03d", i - 57)
            speakers.add(Speaker(id = "${modelId}_zm_$num", index = i, name = "zm_$num (国语男)", gender = "male", locale = "zh-CN", active = false, sampleVoice = ""))
        }
        return TTSModelData(
            name = modelId,
            url = "https://github.com/sunywuu-beep/HandyReader/releases/download/v-debug-test/kokoro-int8-multi-lang-v1_1.zip",
            type = "kokoro",
            locale = "zh-CN",
            size = "139MB",
            base = emptyList(),
            processSpeed = 2.5f,
            quality = 4.8f,
            speakers_num = speakers.size,
            speakers = speakers,
            license = "Apache-2.0",
            licenseUrl = "https://github.com/k2-fsa/sherpa-onnx",
            remark = "Kokoro v1.1 离线高质量多语言 (含55位国语女声/45位国语男声)"
        )
    }

    override suspend fun deleteModel(modelId: String) {
        appDatabase.withTransaction {
            modelDao.deleteModel(modelId)
            speakerDao.deleteSpeakersByModel(modelId)
        }
    }
}