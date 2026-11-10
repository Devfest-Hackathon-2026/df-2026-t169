package com.example.gdgoc_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.ui.screen.onboarding.*
import com.example.gdgoc_app.ui.theme.GDGoCAppTheme

/** Emulator visual review only; absent from release, with no service or API access. */
class ProfileRegistrationPreviewActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GDGoCAppTheme {
                var name by remember {mutableStateOf("")}
                var bio by remember {mutableStateOf("")}
                var submitted by remember {mutableStateOf(false)}
                Column {
                    Text(if(submitted) "UI確認：入力検証OK・保存していません" else "UI確認専用：通信・保存は行いません",Modifier.padding(8.dp))
                    UsernameRegistrationContent(UsernameRegistrationUiState(name=name,bio=bio,
                        canRegister=UsernameRules.error(name)==null && OnboardingBioRules.error(bio)==null,
                        nameError=if(name.isEmpty()) null else UsernameRules.error(name),bioError=OnboardingBioRules.error(bio)),
                        onName={name=it},onRegister={submitted=true},onBio={bio=it})
                }
            }
        }
    }
}
