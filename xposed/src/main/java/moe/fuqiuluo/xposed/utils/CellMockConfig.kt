package moe.fuqiuluo.xposed.utils

import android.os.Bundle

data class CellMockConfig(
    var enabled: Boolean = false,
    var mcc: Int = 460,
    var mnc: Int = 0,
    var lteTac: Int = 0,
    var lteEci: Int = 0,
    var ltePci: Int = 0,
    var lteEarfcn: Int = 0,
    var nrNci: Long = 0L,
    var nrPci: Int = 0,
    var nrArfcn: Int = 0,
    var preferNr: Boolean = true
) {
    fun writeTo(bundle: Bundle) {
        bundle.putBoolean("cell_mock_enabled", enabled)
        bundle.putInt("cell_mcc", mcc)
        bundle.putInt("cell_mnc", mnc)
        bundle.putInt("cell_lte_tac", lteTac)
        bundle.putInt("cell_lte_eci", lteEci)
        bundle.putInt("cell_lte_pci", ltePci)
        bundle.putInt("cell_lte_earfcn", lteEarfcn)
        bundle.putLong("cell_nr_nci", nrNci)
        bundle.putInt("cell_nr_pci", nrPci)
        bundle.putInt("cell_nr_arfcn", nrArfcn)
        bundle.putBoolean("cell_prefer_nr", preferNr)
    }

    companion object {
        fun from(bundle: Bundle, fallback: CellMockConfig = CellMockConfig()): CellMockConfig {
            return CellMockConfig(
                enabled = bundle.getBoolean("cell_mock_enabled", fallback.enabled),
                mcc = bundle.getInt("cell_mcc", fallback.mcc),
                mnc = bundle.getInt("cell_mnc", fallback.mnc),
                lteTac = bundle.getInt("cell_lte_tac", fallback.lteTac),
                lteEci = bundle.getInt("cell_lte_eci", fallback.lteEci),
                ltePci = bundle.getInt("cell_lte_pci", fallback.ltePci),
                lteEarfcn = bundle.getInt("cell_lte_earfcn", fallback.lteEarfcn),
                nrNci = bundle.getLong("cell_nr_nci", fallback.nrNci),
                nrPci = bundle.getInt("cell_nr_pci", fallback.nrPci),
                nrArfcn = bundle.getInt("cell_nr_arfcn", fallback.nrArfcn),
                preferNr = bundle.getBoolean("cell_prefer_nr", fallback.preferNr)
            )
        }
    }
}
