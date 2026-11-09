package online.aireport.split.ui.expense

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.theme.SplitTheme

/**
 * "Bon scannen": takes a photo with the camera app or picks one from the photo library and
 * hands its URI to [ReceiptActions.onPhoto]. Neither way needs a permission.
 */
@Composable
fun ReceiptScanButton(isScanning: Boolean, enabled: Boolean, actions: ReceiptActions, modifier: Modifier = Modifier) {
    val colors = SplitTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    // Saved, because the app can be killed while the camera app is in front.
    var cameraPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraPhotoUri
        if (saved && uri != null) actions.onPhoto(uri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) actions.onPhoto(uri)
    }

    Box(modifier) {
        SecondaryButton(
            text = stringResource(if (isScanning) R.string.scan_busy else R.string.scan_receipt),
            onClick = { menuOpen = true },
            enabled = enabled,
            loading = isScanning,
            small = true,
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = MaterialTheme.shapes.small,
            containerColor = colors.paper,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.5.dp, colors.ink),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scan_take_photo)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_camera), contentDescription = null, Modifier.size(20.dp)) },
                onClick = {
                    menuOpen = false
                    val uri = actions.newPhotoUri().also { cameraPhotoUri = it }
                    try {
                        takePicture.launch(uri)
                    } catch (_: ActivityNotFoundException) {
                        actions.onCameraUnavailable()
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scan_pick_photo)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_image), contentDescription = null, Modifier.size(20.dp)) },
                onClick = {
                    menuOpen = false
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }
    }
}
