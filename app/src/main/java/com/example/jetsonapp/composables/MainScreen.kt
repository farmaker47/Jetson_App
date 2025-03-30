package com.example.jetsonapp.composables


import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jetsonapp.JetsonViewModel

@Composable
fun MainScreen(jetsonViewModel: JetsonViewModel = hiltViewModel()) {
    var typedInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val serverResult by jetsonViewModel.serverResult.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "Write something and send it to Jetson!",
            color = Color.Black,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = typedInput,
            onValueChange = { newText -> typedInput = newText },
            placeholder = { Text(text = "Enter prompt...", color = Color.Gray) },
            textStyle = TextStyle(
                color = Color.Black,
                fontSize = 20.sp,
                textAlign = TextAlign.Start
            ),
            singleLine = false,
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                unfocusedTextColor = Color.Black,
                focusedTextColor = Color.Black,
                cursorColor = Color.Black,
                focusedIndicatorColor = Color.Black,
                unfocusedIndicatorColor = Color.Black
            ),
            keyboardActions =
            KeyboardActions(
                onSend = {
                    focusManager.clearFocus(true)
                    focusRequester.freeFocus()
                }
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            jetsonViewModel.updateUserPrompt(typedInput)
            jetsonViewModel.sendData()
            focusManager.clearFocus(true)
            focusRequester.freeFocus()
        }, modifier = Modifier.align(Alignment.End)) {
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

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(width = 1.dp, color = Color.Gray, shape = RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(scrollState)
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = serverResult,
                    color = Color.Black,
                    fontSize = 20.sp,
                    style = TextStyle(lineHeight = 32.sp),
                    textAlign = TextAlign.Start
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}
