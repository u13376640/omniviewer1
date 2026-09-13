package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaFormat
import com.example.data.model.MediaType
import com.example.ui.theme.PrimaryViolet
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated

@Composable
fun AddUrlDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, url: String, type: MediaType, format: MediaFormat) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf(MediaFormat.MP4) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("add_url_dialog"),
        containerColor = SurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = PrimaryViolet.copy(alpha = 0.2f),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = PrimaryViolet,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Add Cloud Media Stream",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enter direct HTTP/HTTPS stream link for MP4 video, PDF document, or JPG/PNG image:",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        val lower = it.lowercase()
                        when {
                            lower.contains(".mp4") -> selectedFormat = MediaFormat.MP4
                            lower.contains(".pdf") -> selectedFormat = MediaFormat.PDF
                            lower.contains(".png") -> selectedFormat = MediaFormat.PNG
                            lower.contains(".jpg") || lower.contains(".jpeg") -> selectedFormat = MediaFormat.JPG
                        }
                    },
                    label = { Text("Stream URL") },
                    placeholder = { Text("https://example.com/video.mp4") },
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null, tint = PrimaryViolet)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryViolet,
                        unfocusedBorderColor = SurfaceElevated
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_url_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (Optional)") },
                    placeholder = { Text("My Stream") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryViolet,
                        unfocusedBorderColor = SurfaceElevated
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_title_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Media Type:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        MediaFormat.MP4 to "Video (MP4)",
                        MediaFormat.PDF to "PDF Comic",
                        MediaFormat.JPG to "Photo (JPG)",
                        MediaFormat.PNG to "Photo (PNG)"
                    ).forEach { (format, label) ->
                        FilterChip(
                            selected = selectedFormat == format,
                            onClick = { selectedFormat = format },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryViolet,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("format_chip_${format.name}")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mediaType = when (selectedFormat) {
                        MediaFormat.MP4 -> MediaType.VIDEO
                        MediaFormat.PDF -> MediaType.PDF
                        MediaFormat.JPG, MediaFormat.PNG -> MediaType.PHOTO
                        MediaFormat.UNKNOWN -> MediaType.PHOTO
                    }
                    onAdd(title.ifBlank { "Cloud ${selectedFormat.name} Stream" }, url, mediaType, selectedFormat)
                },
                enabled = url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("submit_add_url_button")
            ) {
                Text("Open & Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_add_url_button")
            ) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}
