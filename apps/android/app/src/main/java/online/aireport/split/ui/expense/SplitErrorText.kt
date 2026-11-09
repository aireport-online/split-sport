package online.aireport.split.ui.expense

import androidx.annotation.StringRes
import online.aireport.split.R
import online.aireport.split.core.SplitErrorCode

/** The same Dutch messages the server gives for an invalid split. */
@get:StringRes
val SplitErrorCode.messageRes: Int
    get() = when (this) {
        SplitErrorCode.INVALID_AMOUNT -> R.string.split_error_invalid_amount
        SplitErrorCode.NO_PARTICIPANTS -> R.string.split_error_no_participants
        SplitErrorCode.DUPLICATE_MEMBER -> R.string.split_error_duplicate_member
        SplitErrorCode.INVALID_WEIGHT -> R.string.split_error_invalid_weight
        SplitErrorCode.EXACT_SUM_MISMATCH -> R.string.split_error_exact_sum_mismatch
        SplitErrorCode.INVALID_ITEM -> R.string.split_error_invalid_item
        SplitErrorCode.ITEM_UNASSIGNED -> R.string.split_error_item_unassigned
    }
