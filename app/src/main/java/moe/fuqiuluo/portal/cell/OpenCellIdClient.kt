package moe.fuqiuluo.portal.cell

import android.util.Log
import com.alibaba.fastjson2.JSON
import okhttp3.OkHttpClient
import okhttp3.Request
import moe.fuqiuluo.xposed.utils.CellMockConfig
import kotlin.math.abs

object OpenCellIdClient {
    private const val TAG = "OpenCellIdClient"
    private const val MAX_LIMIT = 50

    private val client by lazy {
        OkHttpClient.Builder().build()
    }

    data class QueryResult(
        val config: CellMockConfig?,
        val message: String
    )

    private data class RawCell(
        val radio: String,
        val mcc: Int,
        val mnc: Int,
        val lac: Int,
        val tac: Int,
        val cellId: Long,
        val pci: Int,
        val arfcn: Int,
        val lat: Double,
        val lon: Double
    )

    fun queryBestConfig(lat: Double, lon: Double, token: String, preferNr: Boolean): QueryResult {
        if (token.isBlank()) {
            return QueryResult(null, "OpenCellID token is blank")
        }
        val maskedToken = token.take(6) + "***"
        val deltas = listOf(0.004, 0.007, 0.009)
        Log.d(TAG, "queryBestConfig: lat=$lat lon=$lon preferNr=$preferNr token=$maskedToken")

        var cells: com.alibaba.fastjson2.JSONArray? = null
        var lastError = "unknown"
        for (delta in deltas) {
            val minLon = lon - delta
            val minLat = lat - delta
            val maxLon = lon + delta
            val maxLat = lat + delta
            // BBOX order for this API is: minLat,minLon,maxLat,maxLon
            val url = "https://opencellid.org/cell/getInArea?key=$token&BBOX=$minLat,$minLon,$maxLat,$maxLon&format=json&limit=$MAX_LIMIT"
            val request = Request.Builder().url(url).get().build()
            Log.d(TAG, "queryBestConfig attempt: delta=$delta bbox=$minLat,$minLon,$maxLat,$maxLon limit=$MAX_LIMIT")

            val body = kotlin.runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val msg = "OpenCellID http=${response.code}"
                        Log.e(TAG, msg)
                        lastError = msg
                        return@use null
                    }
                    response.body?.string()
                }
            }.getOrElse {
                val msg = "OpenCellID request error: ${it.message ?: it.javaClass.simpleName}"
                Log.e(TAG, msg, it)
                lastError = msg
                null
            } ?: continue

            val root = JSON.parseObject(body)
            if (root == null) {
                lastError = "OpenCellID parse root failed"
                continue
            }
            if (root.containsKey("error")) {
                val error = root.getString("error") ?: "unknown error"
                val code = if (root.containsKey("code")) root.getIntValue("code") else -1
                val msg = "OpenCellID error(code=$code): $error"
                Log.e(TAG, msg)
                lastError = msg
                // code=1 means not found in area, retry with larger BBOX
                if (code == 1) continue
                return QueryResult(null, msg)
            }

            val areaCells = root.getJSONArray("cells")
            if (areaCells == null) {
                val keys = root.keys.joinToString(",")
                val preview = body.take(200)
                val msg = "OpenCellID missing cells field; keys=[$keys], body=$preview"
                Log.e(TAG, msg)
                lastError = msg
                continue
            }
            if (areaCells.isEmpty()) {
                val msg = "OpenCellID cells is empty"
                Log.e(TAG, msg)
                lastError = msg
                continue
            }
            cells = areaCells
            break
        }

        if (cells == null) {
            return QueryResult(null, lastError)
        }

        var bestLte: RawCell? = null
        var bestNr: RawCell? = null
        var bestLteDist = Double.MAX_VALUE
        var bestNrDist = Double.MAX_VALUE

        for (i in 0 until cells.size) {
            val obj = cells.getJSONObject(i) ?: continue
            val raw = parseRawCell(obj) ?: continue
            val dist = abs(raw.lat - lat) + abs(raw.lon - lon)
            val radio = raw.radio.uppercase()
            if (radio.contains("NR") || radio.contains("5G")) {
                if (dist < bestNrDist) {
                    bestNrDist = dist
                    bestNr = raw
                }
            } else if (radio.contains("LTE") || radio.contains("4G")) {
                if (dist < bestLteDist) {
                    bestLteDist = dist
                    bestLte = raw
                }
            }
        }

        val anchor = (if (preferNr) bestNr ?: bestLte else bestLte ?: bestNr)
            ?: return QueryResult(null, "OpenCellID has no LTE/NR records in area")
        val lte = bestLte ?: anchor
        val nr = bestNr ?: anchor

        val config = CellMockConfig(
            enabled = true,
            mcc = anchor.mcc,
            mnc = anchor.mnc,
            lteTac = if (lte.tac != 0) lte.tac else lte.lac,
            lteEci = lte.cellId.toInt(),
            ltePci = lte.pci,
            lteEarfcn = lte.arfcn,
            nrNci = nr.cellId,
            nrPci = nr.pci,
            nrArfcn = nr.arfcn,
            preferNr = preferNr
        )
        Log.d(TAG, "queryBestConfig success: mcc=${config.mcc} mnc=${config.mnc} lteTac=${config.lteTac} lteEci=${config.lteEci} nrNci=${config.nrNci}")
        return QueryResult(config, "ok")
    }

    private fun parseRawCell(obj: com.alibaba.fastjson2.JSONObject): RawCell? {
        val radio = obj.getString("radio") ?: return null
        val lat = obj.getDoubleValue("lat")
        val lon = obj.getDoubleValue("lon")
        if (lat == 0.0 && lon == 0.0) return null

        val cellId = when {
            obj.containsKey("cellid") -> obj.getLongValue("cellid")
            obj.containsKey("cid") -> obj.getLongValue("cid")
            else -> 0L
        }
        val tac = if (obj.containsKey("tac")) obj.getIntValue("tac") else 0
        val lac = if (obj.containsKey("lac")) obj.getIntValue("lac") else 0
        val pci = if (obj.containsKey("pci")) obj.getIntValue("pci") else 0
        val arfcn = when {
            obj.containsKey("earfcn") -> obj.getIntValue("earfcn")
            obj.containsKey("nrarfcn") -> obj.getIntValue("nrarfcn")
            obj.containsKey("arfcn") -> obj.getIntValue("arfcn")
            else -> 0
        }

        return RawCell(
            radio = radio,
            mcc = if (obj.containsKey("mcc")) obj.getIntValue("mcc") else 460,
            mnc = if (obj.containsKey("mnc")) obj.getIntValue("mnc") else 0,
            lac = lac,
            tac = tac,
            cellId = cellId,
            pci = pci,
            arfcn = arfcn,
            lat = lat,
            lon = lon
        )
    }
}
