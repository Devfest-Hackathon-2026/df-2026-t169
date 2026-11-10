package com.example.gdgoc_app.diagnosis

import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.data.remote.*
import com.example.gdgoc_app.data.repository.ApiUserRepository
import com.example.gdgoc_app.ui.navigation.onboardingRoute
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class RegistrationContractTest {
    private fun repository(enabled:Boolean=true,respond:(Request)->Pair<Int,String>):ApiOnboardingRepository {
        val http=OkHttpClient.Builder().addInterceptor { chain ->
            val (code,body)=respond(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test").body(body.toResponseBody()).build()
        }.build()
        val api=ApiClient("https://example.invalid",client=http,tokenProvider={"synthetic"},errorLogger={})
        return ApiOnboardingRepository(api,ApiUserRepository(api),enabled)
    }
    @Test fun disabledContractCannotPerformEitherRequest() = runTest {
        var calls=0
        val repo=repository(false) { calls++;200 to "{}" }
        try { repo.registration();fail() } catch (_:DiagnosisNotConnected) { }
        try { repo.registerUsername("名前");fail() } catch (_:DiagnosisNotConnected) { }
        assertEquals(0,calls)
    }
    @Test fun registrationContractRequiresExplicitServerStateAndDiagnosisReference() = runTest {
        val repo=repository { request ->
            assertEquals("GET",request.method);assertEquals("/users/me/registration",request.url.encodedPath)
            assertEquals("Bearer synthetic",request.header("Authorization"))
            200 to """{"registrationState":"PendingUsername","diagnosisRequired":true,"verifiedDiagnosisId":"saved"}"""
        }
        assertEquals(RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"saved"),repo.registration())
        for(body in listOf("{}",
            """{"registrationState":"New","diagnosisRequired":true,"verifiedDiagnosisId":null}""",
            """{"registrationState":"Existing","diagnosisRequired":false,"verifiedDiagnosisId":null}""",
            """{"registrationState":"Completed","diagnosisRequired":false,"verifiedDiagnosisId":null}""",
            """{"registrationState":"PendingDiagnosis","diagnosisRequired":false,"verifiedDiagnosisId":null}""",
            """{"registrationState":"PendingDiagnosis","diagnosisRequired":true,"verifiedDiagnosisId":"unexpected"}""","""{"registrationState":"Existing","diagnosisRequired":true}""",
            """{"registrationState":"PendingUsername","diagnosisRequired":true}""",
            """{"registrationState":"Unknown","diagnosisRequired":false}""")) {
            try { repository { 200 to body }.registration();fail() } catch (_:ApiException) { }
        }
    }
    @Test fun initializeSendsOnlyTrimmedUserEnteredNameAndRequires200Or201() = runTest {
        val profile="""{"id":"account-a","name":"名前","email":"","emailVerified":true,"bio":"","avatarUrl":"","createdAt":"server-time"}"""
        for(code in listOf(200,201,202,400,401,403,404,405,500)) {
            val repo=repository { request ->
                assertEquals("POST",request.method);assertEquals("/users/me/initialize",request.url.encodedPath)
                assertEquals("Bearer synthetic",request.header("Authorization"))
                val body=Buffer();request.body!!.writeTo(body)
                assertEquals("{\"name\":\"名前\"}",body.readUtf8())
                code to profile
            }
            if(code in setOf(200,201)) assertEquals("名前",repo.registerUsername("  名前  ").name)
            else try { repo.registerUsername("名前");fail() } catch(e:ApiException) { assertEquals(code,e.statusCode) }
        }
    }
    @Test fun mandatoryStagesNeverResolveToNormalAppRoutes() {
        assertEquals("onboarding_username",onboardingRoute(OnboardingStage.PendingUsername))
        assertEquals("onboarding_username",onboardingRoute(OnboardingStage.RegisteringUsername))
        assertEquals("onboarding_result",onboardingRoute(OnboardingStage.Result))
        OnboardingStage.entries.forEach { assertTrue(onboardingRoute(it).startsWith("onboarding_")) }
    }
    @Test fun threeValidRegistrationStatesAreParsedWithoutLegacyFallback() = runTest {
        for((state,required,id) in listOf(Triple("PendingDiagnosis",true,"null"),
            Triple("PendingUsername",true,"\"initial\""),Triple("Completed",false,"\"initial\""))) {
            val repo=repository { 200 to """{"registrationState":"$state","diagnosisRequired":$required,"verifiedDiagnosisId":$id}""" }
            assertEquals(state,repo.registration().registrationState.name)
        }
        assertEquals(3,RegistrationStatus.entries.size)
    }
    @Test fun invalidUsernameNeverMakesARegistrationRequest() = runTest {
        var calls=0;val repo=repository { calls++;200 to "{}" }
        for(name in listOf(" ","あ".repeat(31),"名\n前","名\t前","名\u0000前","名\u007f前","名\u0085前")) {
            assertNotNull(UsernameRules.error(name))
            try { repo.registerUsername(name);fail() } catch (_:IllegalArgumentException) { }
        }
        assertEquals(0,calls)
        assertEquals(30,UsernameRules.MAX_LENGTH)
        assertNull(UsernameRules.error("　"+"あ".repeat(30)+" "))
        assertNull(UsernameRules.error("😀".repeat(30)))
        assertNotNull(UsernameRules.error("😀".repeat(31)))
    }
    @Test fun initializeProfileSendsBioAndUsesServerPersistedValue() = runTest {
        val repo=repository { request ->
            assertEquals("POST",request.method);assertEquals("/users/me/initialize",request.url.encodedPath)
            val body=Buffer();request.body!!.writeTo(body)
            val json=com.google.gson.JsonParser.parseString(body.readUtf8()).asJsonObject
            assertEquals(setOf("name","bio"),json.keySet());assertEquals("名前",json["name"].asString);assertEquals("自己紹介",json["bio"].asString)
            201 to """{"id":"account-a","name":"名前","email":"","emailVerified":true,"bio":"自己紹介","avatarUrl":"","createdAt":"server-time"}"""
        }
        assertEquals("自己紹介",repo.registerProfile("  名前  ","自己紹介").bio)
    }
    @Test fun serverIgnoringBioIsNeverReportedAsSaved() = runTest {
        val repo=repository { 201 to """{"id":"account-a","name":"名前","email":"","emailVerified":true,"bio":"","avatarUrl":"","createdAt":"server-time"}""" }
        try { repo.registerProfile("名前","保存したい紹介");fail() } catch(_:ApiException) { }
    }
    @Test fun oversizedBioIsRejectedBeforeAnyRequest() = runTest {
        var calls=0;val repo=repository {calls++;200 to "{}"}
        try {repo.registerProfile("名前","あ".repeat(121));fail()} catch(_:IllegalArgumentException) { }
        assertEquals(0,calls)
    }

    @Test fun profileRequestAndSavedValueCheckUseNormalizedBio() = runTest {
        for(raw in listOf("普通の紹介", "  自己紹介　", " \t\n\u0085\u00a0", "", "　"+"😀".repeat(120)+" ")) {
            val normalized=OnboardingBioRules.normalize(raw)
            var calls=0
            val repo=repository { request ->
                calls++
                val body=Buffer();request.body!!.writeTo(body)
                val json=com.google.gson.JsonParser.parseString(body.readUtf8()).asJsonObject
                assertEquals("名前",json["name"].asString)
                assertEquals(normalized,json["bio"].asString)
                val profile=mapOf("id" to "account-a","name" to "名前","email" to "","emailVerified" to true,
                    "bio" to normalized,"avatarUrl" to "","createdAt" to "server-time")
                201 to com.google.gson.Gson().toJson(profile)
            }
            assertEquals(normalized,repo.registerProfile("  名前  ",raw).bio)
            assertEquals(1,calls)
        }
    }

}
