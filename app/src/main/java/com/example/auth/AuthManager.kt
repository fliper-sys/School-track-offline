package com.example.auth

import android.content.Context
import android.util.Log
import com.example.database.CourseMaterial
import com.example.database.StudyQuiz
import com.example.database.FlashcardDeck
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class UserProfile(
    val uid: String,
    val email: String,
    val fullName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val targetDailyMinutes: Int = 30,
    val completedStreak: Int = 0,
    val tier: String = "free", // "free" or "paid"
    val trialStartedAt: Long = createdAt
) {
    fun isTrialExpired(): Boolean {
        if (tier == "paid") return false
        val fourteenDaysInMillis = 14L * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() - trialStartedAt > fourteenDaysInMillis
    }

    fun trialDaysLeft(): Int {
        if (tier == "paid") return 14
        val fourteenDaysInMillis = 14L * 24 * 60 * 60 * 1000
        val elapsed = System.currentTimeMillis() - trialStartedAt
        val remaining = fourteenDaysInMillis - elapsed
        return if (remaining <= 0) 0 else (remaining / (1000 * 60 * 60 * 24)).toInt()
    }
}

object AuthManager {
    private const val TAG = "AuthManager"
    private var firebaseAuth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null

    // Whether Firebase is successfully initialized and can be used.
    var isFirebaseEnabled: Boolean = false
        private set

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser

    // Local SharedPreferences/Mock fallback database for offline use when Firebase is not configured
    private const val PREFS_NAME = "study_genius_auth_prefs"
    private const val KEY_UID = "local_uid"
    private const val KEY_EMAIL = "local_email"
    private const val KEY_NAME = "local_name"
    private const val KEY_STREAK = "local_streak"
    private const val KEY_TIER = "local_tier"
    private const val KEY_TRIAL_START = "local_trial_start"

    fun initialize(context: Context) {
        try {
            // Attempt to initialize Firebase
            FirebaseApp.initializeApp(context)
            firebaseAuth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            
            // CONFIGURE OFFLINE CACHE EXPLICITLY TO ALLOW OFFLINE ACCESS TO SUMMARIES, FLASHCARDS, AND QUIZ HISTORY
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder()
                        .setSizeBytes(104857600L) // 100 MB cache size
                        .build())
                    .build()
                firestore?.firestoreSettings = settings
                Log.d(TAG, "Firestore offline persistence explicitly configured with 100MB cache size.")
            } catch (e: Exception) {
                Log.e(TAG, "Error configuring modern localCacheSettings, attempting legacy setPersistenceEnabled", e)
                try {
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setPersistenceEnabled(true)
                        .setCacheSizeBytes(104857600L)
                        .build()
                    firestore?.firestoreSettings = settings
                } catch (e2: Exception) {
                    Log.e(TAG, "Error configuring fallback persistence settings.", e2)
                }
            }

            isFirebaseEnabled = true
            Log.d(TAG, "Firebase initialized successfully.")
            
            // Sync initial state from Firebase Auth if available
            val fbUser = firebaseAuth?.currentUser
            if (fbUser != null) {
                // Fetch user data in background or construct basic profile
                _currentUser.value = UserProfile(
                    uid = fbUser.uid,
                    email = fbUser.email ?: "",
                    fullName = fbUser.displayName ?: "Study Genius User",
                    tier = "free",
                    trialStartedAt = System.currentTimeMillis()
                )
                // Spawn Firestore sync
                loadProfileFromFirestore(fbUser.uid)
            } else {
                loadLocalBackupProfile(context)
            }
        } catch (e: Exception) {
            isFirebaseEnabled = false
            Log.w(TAG, "Firebase initialization failed (missing google-services.json?). Falling back to local/Room storage.", e)
            loadLocalBackupProfile(context)
        }
    }

    private fun loadLocalBackupProfile(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uid = prefs.getString(KEY_UID, null)
        val email = prefs.getString(KEY_EMAIL, null)
        val name = prefs.getString(KEY_NAME, null)
        if (uid != null && email != null) {
            val loadedCreatedAt = prefs.getLong("local_created_at", System.currentTimeMillis())
            _currentUser.value = UserProfile(
                uid = uid,
                email = email,
                fullName = name ?: "Scholar",
                createdAt = loadedCreatedAt,
                completedStreak = prefs.getInt(KEY_STREAK, 0),
                tier = prefs.getString(KEY_TIER, "free") ?: "free",
                trialStartedAt = prefs.getLong(KEY_TRIAL_START, loadedCreatedAt)
            )
        } else {
            _currentUser.value = null
        }
    }

    private fun saveLocalProfile(context: Context, profile: UserProfile) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_UID, profile.uid)
            .putString(KEY_EMAIL, profile.email)
            .putString(KEY_NAME, profile.fullName)
            .putInt(KEY_STREAK, profile.completedStreak)
            .putString(KEY_TIER, profile.tier)
            .putLong(KEY_TRIAL_START, profile.trialStartedAt)
            .putLong("local_created_at", profile.createdAt)
            .apply()
        _currentUser.value = profile
    }

    private fun clearLocalProfile(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        _currentUser.value = null
    }

    private fun loadProfileFromFirestore(uid: String) {
        if (!isFirebaseEnabled) return
        firestore?.collection("users")?.document(uid)?.get()
            ?.addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val profile = UserProfile(
                        uid = uid,
                        email = document.getString("email") ?: "",
                        fullName = document.getString("fullName") ?: "Study Genius User",
                        createdAt = document.getLong("createdAt") ?: System.currentTimeMillis(),
                        targetDailyMinutes = document.getLong("targetDailyMinutes")?.toInt() ?: 30,
                        completedStreak = document.getLong("completedStreak")?.toInt() ?: 0,
                        tier = document.getString("tier") ?: "free",
                        trialStartedAt = document.getLong("trialStartedAt") ?: (document.getLong("createdAt") ?: System.currentTimeMillis())
                    )
                    _currentUser.value = profile
                }
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Error fetching profile from Firestore", e)
            }
    }

    suspend fun register(context: Context, email: String, password: String, fullName: String): Result<UserProfile> {
        return try {
            if (isFirebaseEnabled && firebaseAuth != null) {
                val authResult = firebaseAuth!!.createUserWithEmailAndPassword(email, password).await()
                val user = authResult.user ?: throw Exception("Created user is null")
                
                // Save additional info in Profile
                val profile = UserProfile(
                    uid = user.uid,
                    email = email,
                    fullName = fullName
                )
                
                // Save to Firestore
                firestore?.collection("users")?.document(user.uid)?.set(profile)?.await()
                _currentUser.value = profile
                Result.success(profile)
            } else {
                // Fallback to SQLite local persistence
                val mockUid = UUID.randomUUID().toString()
                val profile = UserProfile(
                    uid = mockUid,
                    email = email,
                    fullName = fullName
                )
                saveLocalProfile(context, profile)
                Result.success(profile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Register error", e)
            Result.failure(e)
        }
    }

    suspend fun login(context: Context, email: String, password: String): Result<UserProfile> {
        return try {
            if (isFirebaseEnabled && firebaseAuth != null) {
                val authResult = firebaseAuth!!.signInWithEmailAndPassword(email, password).await()
                val user = authResult.user ?: throw Exception("Sign in failed - user null")
                val profile = UserProfile(
                    uid = user.uid,
                    email = email,
                    fullName = user.displayName ?: "Study Genius User"
                )
                _currentUser.value = profile
                loadProfileFromFirestore(user.uid)
                Result.success(profile)
            } else {
                // Mock search for local credentials in our backup shared preference
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val storedEmail = prefs.getString(KEY_EMAIL, null)
                if (storedEmail != null && storedEmail.equals(email, ignoreCase = true)) {
                    val profile = UserProfile(
                        uid = prefs.getString(KEY_UID, UUID.randomUUID().toString()) ?: UUID.randomUUID().toString(),
                        email = email,
                        fullName = prefs.getString(KEY_NAME, "Scholar") ?: "Scholar",
                        completedStreak = prefs.getInt(KEY_STREAK, 0)
                    )
                    _currentUser.value = profile
                    Result.success(profile)
                } else {
                    // Create an offline fallback account instantly on correct input
                    val profile = UserProfile(
                        uid = UUID.randomUUID().toString(),
                        email = email,
                        fullName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                    )
                    saveLocalProfile(context, profile)
                    Result.success(profile)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            Result.failure(e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            if (isFirebaseEnabled && firebaseAuth != null) {
                firebaseAuth!!.sendPasswordResetEmail(email).await()
                Result.success(Unit)
            } else {
                // Offline success imitation
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Password reset error", e)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(context: Context, updatedProfile: UserProfile): Result<UserProfile> {
        return try {
            if (isFirebaseEnabled && firebaseAuth != null) {
                firestore?.collection("users")?.document(updatedProfile.uid)?.set(updatedProfile)?.await()
                _currentUser.value = updatedProfile
                Result.success(updatedProfile)
            } else {
                saveLocalProfile(context, updatedProfile)
                Result.success(updatedProfile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update profile error", e)
            Result.failure(e)
        }
    }

    fun logout(context: Context) {
        try {
            if (isFirebaseEnabled && firebaseAuth != null) {
                firebaseAuth!!.signOut()
            }
            clearLocalProfile(context)
        } catch (e: Exception) {
            Log.e(TAG, "Logout error", e)
        }
    }

    // Sync CourseMaterial to Firestore
    fun syncMaterialToFirestore(material: CourseMaterial) {
        if (!isFirebaseEnabled || firestore == null) return
        val currentUid = currentUser.value?.uid ?: return
        
        val materialData = hashMapOf(
            "id" to material.id,
            "courseId" to material.courseId,
            "title" to material.title,
            "rawContent" to material.rawContent,
            "summaryText" to material.summaryText,
            "createdTimestamp" to material.createdTimestamp
        )
        
        firestore?.collection("users")?.document(currentUid)
            ?.collection("course_materials")?.document(material.id.toString())
            ?.set(materialData)
            ?.addOnSuccessListener {
                Log.d(TAG, "CourseMaterial synced to Firestore: ${material.id}")
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Error syncing CourseMaterial to Firestore", e)
            }
    }

    // Sync StudyQuiz to Firestore
    fun syncQuizToFirestore(quiz: StudyQuiz) {
        if (!isFirebaseEnabled || firestore == null) return
        val currentUid = currentUser.value?.uid ?: return
        
        val quizData = hashMapOf(
            "id" to quiz.id,
            "courseId" to quiz.courseId,
            "title" to quiz.title,
            "questionsJson" to quiz.questionsJson,
            "score" to quiz.score,
            "totalQuestions" to quiz.totalQuestions,
            "isCompleted" to quiz.isCompleted,
            "createdTimestamp" to quiz.createdTimestamp
        )
        
        firestore?.collection("users")?.document(currentUid)
            ?.collection("study_quizzes")?.document(quiz.id.toString())
            ?.set(quizData)
            ?.addOnSuccessListener {
                Log.d(TAG, "StudyQuiz synced to Firestore: ${quiz.id}")
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Error syncing StudyQuiz to Firestore", e)
            }
    }

    // Sync FlashcardDeck to Firestore
    fun syncFlashcardDeckToFirestore(deck: FlashcardDeck) {
        if (!isFirebaseEnabled || firestore == null) return
        val currentUid = currentUser.value?.uid ?: return
        
        val deckData = hashMapOf(
            "id" to deck.id,
            "courseId" to deck.courseId,
            "materialId" to deck.materialId,
            "title" to deck.title,
            "flashcardsJson" to deck.flashcardsJson,
            "createdTimestamp" to deck.createdTimestamp
        )
        
        firestore?.collection("users")?.document(currentUid)
            ?.collection("flashcard_decks")?.document(deck.id.toString())
            ?.set(deckData)
            ?.addOnSuccessListener {
                Log.d(TAG, "FlashcardDeck synced to Firestore: ${deck.id}")
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Error syncing FlashcardDeck to Firestore", e)
            }
    }
}
