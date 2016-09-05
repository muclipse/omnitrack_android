package kr.ac.snu.hcil.omnitrack.utils

import kr.ac.snu.hcil.omnitrack.R
import org.atteo.evo.inflector.English
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

/**
 * Created by younghokim on 16. 9. 5..
 */
class NumberStyle {

    class FormattedInformation(var unitPart: String?, var numberPart: String, var unitPosition: UnitPosition) {
        val unitPartStart: Int get() {
            return when (unitPosition) {
                UnitPosition.Rear -> numberPart.length + 1
                UnitPosition.Front -> 0
                UnitPosition.None -> 0
            }
        }

        val unitPartEnd: Int get() {
            return unitPartStart + (unitPart?.length ?: 0)
        }

        val numberPartStart: Int get() {
            return when (unitPosition) {
                UnitPosition.Rear -> 0
                UnitPosition.Front -> (unitPart?.length ?: 0) + 1
                UnitPosition.None -> 0
            }
        }

        val numberPartEnd: Int get() {
            return numberPartStart + numberPart.length
        }


    }

    enum class UnitPosition(val nameResId: Int) {
        Front(R.string.msg_number_style_unit_position_front),
        Rear(R.string.msg_number_style_unit_position_rear),
        None(R.string.msg_number_style_unit_position_none)
    }

    var unitPosition: UnitPosition
        get() {
            return UnitPosition.values()[unitPositionId]
        }
        set(value) {
            unitPositionId = value.ordinal
        }

    private var unitPositionId: Int = UnitPosition.None.ordinal

    var unit: String = ""
    var pluralizeUnit: Boolean = false
    var fractionPart: Int = 0
    var commaUnit: Int = 3

    fun makeDecimalFormat(): DecimalFormat {
        //example: DecimalFormat("#,###.###")

        val formatStringBuilder = StringBuilder().append('#')
        if (commaUnit > 0) {
            formatStringBuilder.append(",")
            for (i in 0..commaUnit - 1) {
                formatStringBuilder.append('#')
            }
        }

        if (fractionPart > 0) {
            formatStringBuilder.append('.')
            for (i in 0..fractionPart - 1) {
                formatStringBuilder.append('#')
            }
        }

        val format = DecimalFormat(formatStringBuilder.toString())

        if (fractionPart == 0) {
            format.roundingMode = RoundingMode.FLOOR
        }

        return format
    }

    fun formatNumber(number: Any, infoOut: FormattedInformation? = null): String {
        infoOut?.unitPosition = UnitPosition.None
        infoOut?.unitPart = null

        if (isNumericPrimitive(number) || number is BigDecimal) {
            val formattedNumber = makeDecimalFormat().format(number)
            infoOut?.numberPart = formattedNumber

            if (unit.isNotBlank() || unitPosition == UnitPosition.None) {

                val pluralized = if (pluralizeUnit) {
                    English.plural(unit, Math.ceil(
                            if (number is Int) number.toDouble()
                            else if (number is Long) number.toDouble()
                            else if (number is Float) number.toDouble()
                            else if (number is Double) number
                            else if (number is BigDecimal) number.toDouble()
                            else 0.0
                    ).toInt())
                } else unit

                infoOut?.unitPart = pluralized
                infoOut?.unitPosition = unitPosition

                when (unitPosition) {
                    UnitPosition.Front ->
                        return pluralized + ' ' + formattedNumber
                    UnitPosition.Rear ->
                        return formattedNumber + ' ' + pluralized
                    UnitPosition.None ->
                        return formattedNumber
                }
            } else return formattedNumber
        } else {
            infoOut?.numberPart = number.toString()
            return number.toString()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        } else if (other is NumberStyle) {

            return other.pluralizeUnit == pluralizeUnit &&
                    other.unitPositionId == unitPositionId &&
                    other.unit == unit &&
                    other.fractionPart == fractionPart &&
                    other.commaUnit == commaUnit

        } else return false


    }
}