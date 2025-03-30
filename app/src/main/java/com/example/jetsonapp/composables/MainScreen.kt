package com.example.jetsonapp.composables


import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.jetsonapp.JetsonViewModel

@Composable
fun MainScreen(infoViewModel: JetsonViewModel = hiltViewModel()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 2.dp, color = Color.Black, shape = RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "infoViewModel.topText",
                color = Color.Black,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(lineHeight = 32.sp),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Middle Element: Button sized to wrap its content.
        Button(onClick = { infoViewModel.sendData() }, modifier = Modifier.align(Alignment.End)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Send",
                    color = Color.Black,
                    fontSize = 24.sp,
                )
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Icon"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom Element: Box that takes up all the remaining space.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(width = 1.dp, color = Color.Gray, shape = RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "infoViewModel.serverResult",
                color = Color.Black,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(lineHeight = 32.sp),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}
