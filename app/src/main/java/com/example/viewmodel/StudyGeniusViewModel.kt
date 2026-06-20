package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.database.AppDatabase
import com.example.database.AssignmentSolution
import com.example.database.Course
import com.example.database.CourseMaterial
import com.example.database.StudyGeniusRepository
import com.example.database.StudyQuiz
import com.example.database.Flashcard
import com.example.database.FlashcardDeck
import com.example.auth.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class StudyGeniusViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = StudyGeniusRepository(
        database.courseDao(),
        database.materialDao(),
        database.assignmentDao(),
        database.quizDao(),
        database.flashcardDao()
    )

    // Exposed lists of elements
    val courses: StateFlow<List<Course>> = repository.allCourses.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Active navigation tab
    val activeTab = MutableStateFlow("Dashboard") // Options: Dashboard, Material, Assignment, TestPrep, Assistant

    // Selected Course state. If null, displays select/create course on home
    val selectedCourse = MutableStateFlow<Course?>(null)

    // Filtered lists for the active course
    val activeMaterials: StateFlow<List<CourseMaterial>> = combine(
        selectedCourse,
        repository.allMaterials
    ) { course, materials ->
        if (course == null) emptyList()
        else materials.filter { it.courseId == course.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAssignments: StateFlow<List<AssignmentSolution>> = combine(
        selectedCourse,
        repository.allAssignments
    ) { course, assignments ->
        if (course == null) emptyList()
        else assignments.filter { it.courseId == course.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeQuizzes: StateFlow<List<StudyQuiz>> = combine(
        selectedCourse,
        repository.allQuizzes
    ) { course, quizzes ->
        if (course == null) emptyList()
        else quizzes.filter { it.courseId == course.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeFlashcardDecks: StateFlow<List<FlashcardDeck>> = combine(
        selectedCourse,
        repository.allFlashcardDecks
    ) { course, decks ->
        if (course == null) emptyList()
        else decks.filter { it.courseId == course.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentMaterialFlashcards = MutableStateFlow<List<Flashcard>>(emptyList())
    val currentFlashcardDeck = MutableStateFlow<FlashcardDeck?>(null)
    val isDarkTheme = MutableStateFlow(false)
    val themeMode = MutableStateFlow("light") // "light", "dark", "ash"

    // UI Loading and Error states
    val isLoading = MutableStateFlow(false)
    val isFetchingUrl = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)

    // Active items selected for viewing
    val currentSelectedMaterial = MutableStateFlow<CourseMaterial?>(null)
    val currentSelectedAssignment = MutableStateFlow<AssignmentSolution?>(null)
    val currentSelectedQuiz = MutableStateFlow<StudyQuiz?>(null)

    // Interactive Quiz Session properties
    val quizQuestions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val quizCurrentQuestionIndex = MutableStateFlow(0)
    val quizSelectedOptionIndex = MutableStateFlow<Int?>(null)
    val quizScore = MutableStateFlow(0)
    val quizShowExplanation = MutableStateFlow(false)
    val quizChecked = MutableStateFlow(false)

    // AI Chat History inside the specific course assistant tab
    val chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())

    // AI Chat History for clarifying questions about the selected study material/document
    val currentMaterialChatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())

    // Shared flow to emit event notifications (like "Summary Created Successfully")
    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    init {
        // Populate standard courses and content if the list is completely empty
        viewModelScope.launch {
            val currentCourses = repository.allCourses.stateIn(viewModelScope).firstOrNull() ?: emptyList()
            if (currentCourses.isEmpty()) {
                prepopulateSampleData()
            } else {
                selectedCourse.value = currentCourses.firstOrNull()
            }
        }

        // Reactively populate/clear material-specific chat history
        viewModelScope.launch {
            currentSelectedMaterial.collect { material ->
                if (material != null) {
                    currentMaterialChatHistory.value = listOf(
                        ChatMessage(
                            content = "Hi there! I have fully processed '${material.title}'. Feel free to ask me any clarifying questions, or ask me to explain specific concepts directly from this document!",
                            isUser = false
                        )
                    )
                } else {
                    currentMaterialChatHistory.value = emptyList()
                }
            }
        }
    }

    private suspend fun prepopulateSampleData() {
        withContext(Dispatchers.IO) {
            val c1Id = repository.insertCourse(
                Course(
                    name = "Computer Science 101",
                    description = "Fundamental principles of programming, CPU architecture, memory management, and data structures."
                )
            )
            val c2Id = repository.insertCourse(
                Course(
                    name = "General Physics I",
                    description = "Classical mechanics, Newton's laws of motion, gravity, kinetic energy, and rotational dynamics."
                )
            )

            // Prep some basic materials for CS 101
            repository.insertMaterial(
                CourseMaterial(
                    courseId = c1Id,
                    title = "Lecture 1: The CPU & Memory Hierarchy",
                    rawContent = "Computers execute program instructions using a Central Processing Unit. Register memory, cache (L1, L2, L3), RAM, and Secondary Storage form the memory hierarchy. Fast memory is expensive and small, while large memory is cheap and slow.",
                    summaryText = "### CS 101 Lecture 1 Summary\n\n- **CPU (Central Processing Unit)**: The brain of the computer that fetches, decodes, and executes instructions.\n\n- **Memory Hierarchy**:\n1. **Registers**: Extremely fast, inside the CPU, store immediate operands.\n2. **Cache Memory (L1, L2, L3)**: High-speed static RAM bridging CPU registers and main RAM.\n3. **Volatile RAM**: Working memory for active processes. Comparatively cheap yet slower than cache.\n4. **Non-Volatile Storage**: Solid State Drives (SSDs) or hard disks. Permanent, vast storage but slowest access times."
                )
            )

            // Setup a default quiz
            val mockCsQuestions = """
                [
                  {
                    "question": "Which of the following is the fastest memory inside a computer system?",
                    "options": ["L3 Cache", "RAM (Random Access Memory)", "CPU Registers", "SSD Storage"],
                    "answerIndex": 2,
                    "explanation": "CPU Registers are located directly inside the CPU processor core and have the absolute lowest access latency (typically less than 1 nanosecond)."
                  },
                  {
                    "question": "What does volatile memory mean?",
                    "options": ["The data is permanent", "The data is lost when the power is turned off", "The memory is extremely unstable and crashes", "The memory cannot be read"],
                    "answerIndex": 1,
                    "explanation": "Volatile memory, like RAM, is high-speed temporary storage that requires a continuous electrical current to retain its data contents."
                  }
                ]
            """.trimIndent()

            repository.insertQuiz(
                StudyQuiz(
                    courseId = c1Id,
                    title = "Diagnostic Placement Test",
                    questionsJson = mockCsQuestions,
                    totalQuestions = 2,
                    score = 1,
                    isCompleted = true,
                    createdTimestamp = System.currentTimeMillis() - 4 * 24 * 3600 * 1000L // 4 days ago
                )
            )

            repository.insertQuiz(
                StudyQuiz(
                    courseId = c1Id,
                    title = "Midterm Concepts Speed Review",
                    questionsJson = mockCsQuestions,
                    totalQuestions = 5,
                    score = 4,
                    isCompleted = true,
                    createdTimestamp = System.currentTimeMillis() - 2 * 24 * 3600 * 1000L // 2 days ago
                )
            )

            repository.insertQuiz(
                StudyQuiz(
                    courseId = c1Id,
                    title = "CPU Architecture Deep-Dive Quiz",
                    questionsJson = mockCsQuestions,
                    totalQuestions = 3,
                    score = 3,
                    isCompleted = true,
                    createdTimestamp = System.currentTimeMillis() - 12 * 3600 * 1000L // 12 hours ago
                )
            )

            repository.insertQuiz(
                StudyQuiz(
                    courseId = c1Id,
                    title = "CPU & Memory Basics Practice Test",
                    questionsJson = mockCsQuestions,
                    totalQuestions = 2,
                    score = 0,
                    isCompleted = false,
                    createdTimestamp = System.currentTimeMillis()
                )
            )

            // Fetch populated courses to set default selection
            val freshCourses = repository.allCourses.firstOrNull() ?: emptyList()
            if (freshCourses.isNotEmpty()) {
                selectedCourse.value = freshCourses.firstOrNull { it.id == c1Id } ?: freshCourses.firstOrNull()
            }
        }
    }

    // Is the API Key currently default or placeholder?
    fun isApiKeyPlaceholder(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.trim().isEmpty() || key.contains("MY_GEMINI_API_KEY") || key == "GEMINI_API_KEY"
    }

    // SELECT COURSE
    fun selectCourse(course: Course?) {
        selectedCourse.value = course
        // Reset navigation states and details when switching courses
        currentSelectedMaterial.value = null
        currentSelectedAssignment.value = null
        currentSelectedQuiz.value = null
        chatHistory.value = emptyList()
        quizQuestions.value = emptyList()
    }

    // CREATE COURSE
    fun createCourse(name: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (name.isBlank()) return@launch
            val newId = repository.insertCourse(Course(name = name, description = description))
            val freshCourses = repository.allCourses.firstOrNull() ?: emptyList()
            val created = freshCourses.find { it.id == newId }
            if (created != null) {
                withContext(Dispatchers.Main) {
                    selectCourse(created)
                    viewModelScope.launch {
                        _uiEvents.emit("Course '${name}' created successfully!")
                    }
                }
            }
        }
    }

    // DELETE COURSE
    fun deleteCourse(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCourse(id)
            if (selectedCourse.value?.id == id) {
                val remaining = repository.allCourses.firstOrNull() ?: emptyList()
                withContext(Dispatchers.Main) {
                    selectCourse(remaining.firstOrNull())
                }
            }
        }
    }

    // GENERATE SUMMARIES (COURSES MATERIAL SUMMARY)
    fun generateCourseSummary(courseId: Int, title: String, rawText: String) {
        if (title.isBlank() || rawText.isBlank()) {
            errorMessage.value = "Please fill in all summary options and lecture source notes."
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val promptText = """
                    You are an expert academic tutor. Summarize the following lecture notes or course readings into a comprehensive, beautiful study guide.
                    Use professional Markdown formatting with neat bullet points, bold definitions, structural chapters/sections, and an optional visual ASCII diagram if it clarifies the topic.
                    
                    Course Topic ID: $courseId
                    Study Material Title: $title
                    
                    Raw Material Content to Summarize:
                    $rawText
                    
                    Respond with only the final beautifully structured markdown summary. Do not include introductory conversational pleasantries.
                """.trimIndent()

                val summaryResult = if (isApiKeyPlaceholder()) {
                    simulateSummaryFallback(title, rawText)
                } else {
                    val request = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = promptText)))),
                        generationConfig = GenerationConfig(temperature = 0.4f)
                    )
                    val result = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                    RetrofitClient.extractText(result)
                }

                if (summaryResult.startsWith("Error:") || summaryResult.trim().isEmpty()) {
                    throw Exception(if (summaryResult.isEmpty()) "Empty response from AI engine" else summaryResult)
                }

                val entryId = withContext(Dispatchers.IO) {
                    repository.insertMaterial(
                        CourseMaterial(
                            courseId = courseId,
                            title = title,
                            rawContent = rawText,
                            summaryText = summaryResult
                        )
                    )
                }

                // Retrieve saved entry and set as selected
                val savedMaterials = repository.getMaterialsForCourse(courseId).firstOrNull() ?: emptyList()
                val newlyCreatedMaterial = savedMaterials.find { it.id == entryId.toInt() }
                currentSelectedMaterial.value = newlyCreatedMaterial

                if (newlyCreatedMaterial != null) {
                    AuthManager.syncMaterialToFirestore(newlyCreatedMaterial)
                }

                _uiEvents.emit("Material summary generated & saved successfully!")
            } catch (e: Exception) {
                Log.e("StudyGenius", "Error generating material summary", e)
                errorMessage.value = "Failed to generate summary: ${e.message}. (You can still save notes with simulated AI summaries if Offline/API key is disabled)."
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun simulateSummaryFallback(title: String, rawContent: String): String {
        return """
            ### 📜 StudyGenius Offline Summary: $title
            *(Notice: Active Demo Mode - Using local parsing because no Gemini API Key is configured via Secrets panel).*
            
            - **Primary Topic**: Integrated Synthesis of course readings.
            - **Source Material Analyzed**: ${if (rawContent.length > 80) rawContent.take(80) + "..." else rawContent}
            
            #### 📌 Core Concept Breakdown
            1. **High-Value Mechanics**: The material describes core parameters of study, emphasizing sequential structures and foundational theories.
            2. **Applied Methodologies**: Students should review definitions, formula variables, and causal relationships in these logs.
            3. **Key Terminology**:
               - *Context Nodes*: Segmented reading blocks.
               - *Synthesis Bridge*: Logical transition to active test preps.
            
            #### 📝 Study Checklist & Key Takeaways
            - [ ] Review raw lecture transcript once more.
            - [ ] Generate self-study practice questions to verify retention.
            - [ ] Build clean diagrams modeling the architectural hierarchies.
        """.trimIndent()
    }

    fun fetchContentFromUrl(url: String, onFinished: (String) -> Unit) {
        if (url.isBlank()) {
            errorMessage.value = "Please provide a valid lecture transcript URL."
            return
        }
        
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }

        viewModelScope.launch {
            isFetchingUrl.value = true
            errorMessage.value = null
            try {
                val cleanedText = withContext(Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val request = okhttp3.Request.Builder()
                        .url(formattedUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP error code: ${response.code}")
                        val rawBody = response.body?.string() ?: throw Exception("Empty body returned from URL")
                        cleanHtmlToPlainText(rawBody)
                    }
                }
                
                onFinished(cleanedText)
                _uiEvents.emit("Document content successfully retrieved and parsed from URL!")
            } catch (e: Exception) {
                Log.e("StudyGenius", "Error fetching URL: $formattedUrl", e)
                errorMessage.value = "Failed to fetch from URL: ${e.message}"
            } finally {
                isFetchingUrl.value = false
            }
        }
    }

    private fun cleanHtmlToPlainText(html: String): String {
        var text = html
        text = text.replace(Regex("(?s)<script.*?>.*?</script>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("(?s)<style.*?>.*?</style>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<p.*?>", RegexOption.IGNORE_CASE), "\n\n")
        text = text.replace(Regex("<li.*?>", RegexOption.IGNORE_CASE), "\n- ")
        text = text.replace(Regex("<h[1-6].*?>", RegexOption.IGNORE_CASE), "\n\n### ")
        text = text.replace(Regex("<[^>]*>"), "")
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        
        val lines = text.split("\n")
        val cleanedLines = lines.map { it.trim() }.filter { it.isNotEmpty() }
        return cleanedLines.joinToString("\n")
    }

    // GENERATE ASSIGNMENT WORK SHEET & SOLUTIONS (STUDENT ASSIGNMENT SOLUTIONS GENERATION)
    fun generateAssignmentSolution(courseId: Int, title: String, problemsText: String, instructions: String) {
        if (title.isBlank() || problemsText.isBlank()) {
            errorMessage.value = "Please fill in the assignment title and problem requirements."
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val systemPrompt = """
                    You are an elite professor and academic solution generator. Compile a pristine, pedagogical 'Solutions Guide' document for the student’s assignment questions.
                    
                    For every problem listed:
                    1. Repeat the Problem text clearly.
                    2. Show the mathematical, logical or conceptual theory applied in detailed LaTeX-like, Markdown formulas or step-by-step paragraphs.
                    3. Write out the final derived answer boldly.
                    4. Outline a 'Key Tutorial Tip' explaining *why* this path is chosen so the student can study and learn, rather than just copying.
                    
                    Tone: Instructive, academic, precise. Use structured chapters and formatted Markdown grids if appropriate.
                """.trimIndent()

                val prompt = """
                    Please solve the following assignment questions.
                    
                    Assignment Title: $title
                    Additional Formatting Instructions: $instructions
                    
                    Problems to Solve:
                    $problemsText
                    
                    Provide the final, fully generated Solutions Guide document. Include no conversational preamble.
                """.trimIndent()

                val documentResult = if (isApiKeyPlaceholder()) {
                    simulateAssignmentFallback(title, problemsText, instructions)
                } else {
                    val request = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                        generationConfig = GenerationConfig(temperature = 0.3f),
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                    )
                    val result = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                    RetrofitClient.extractText(result)
                }

                if (documentResult.trim().isEmpty()) {
                    throw Exception("Empty response from solutions generator engine")
                }

                val assignmentId = withContext(Dispatchers.IO) {
                    repository.insertAssignment(
                        AssignmentSolution(
                            courseId = courseId,
                            title = title,
                            problemsText = problemsText,
                            instructions = instructions,
                            solutionsDocumentText = documentResult
                        )
                    )
                }

                val savedDocs = repository.getAssignmentsForCourse(courseId).firstOrNull() ?: emptyList()
                currentSelectedAssignment.value = savedDocs.find { it.id == assignmentId.toInt() }

                _uiEvents.emit("Assignment solutions guide compiled successfully!")
            } catch (e: Exception) {
                Log.e("StudyGenius", "Error compiling assignment solution", e)
                errorMessage.value = "Failed to solve assignment: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun simulateAssignmentFallback(title: String, problems: String, instructions: String): String {
        return """
            # 📝 SOLUTIONS GUIDE: $title
            *(Standard demo mode document generated offline because Gemini API Key is not set).*
            
            ## 📌 Solution Matrix & Explanations
            
            ### Problem 1 Breakdown & Mechanics
            *Question Analysis*: ${if (problems.length > 100) problems.take(100) + "..." else problems}
            
            #### Step-by-Step Logic
            1. **Isolate known constraints**: Establish variables from the student's prompt.
            2. **Apply governing principles**: Utilize standard core formulas matching the curriculum topic.
            3. **Perform algebraic/conceptual execution**: Evaluate terms sequentially.
            4. **Perform verification checks**: Sanity check values.
            
            #### Derived Solution
            **Final Value/Thesis**: `Success State (Fully Formulated)`
            
            ---
            
            ### 🎓 Study Tutor Tip
            - Focus on the transition step where boundary conditions are applied.
            - Ensure structural formatting matches standard scholastic requirements (e.g., $instructions).
        """.trimIndent()
    }

    // TEST PREP STATION: GENERATE QUIZ (MOCK TEST PREP GENERATION)
    // TEST PREP STATION: GENERATE QUIZ (MOCK TEST PREP GENERATION)
    fun generateTestPrepQuiz(
        courseId: Int, 
        title: String, 
        topic: String, 
        difficulty: String = "Medium",
        numQuestions: Int = 4,
        materialContent: String? = null
    ) {
        if (title.isBlank()) {
            errorMessage.value = "Please input a quiz title."
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val basePrompt = if (!materialContent.isNullOrBlank()) {
                    "Generate a practice study test quiz with exactly $numQuestions multiple-choice questions of $difficulty difficulty level, based on this study material text:\n\n\"\"\"\n$materialContent\n\"\"\"\n\nFocus on the topic or syllabus: \"$topic\". Ensure the complexity of questions reflects the $difficulty difficulty level."
                } else {
                    "Generate a practice study test quiz with exactly $numQuestions multiple-choice questions of $difficulty difficulty level on this educational topic: \"$topic\"."
                }

                val promptText = """
                    $basePrompt
                    You MUST respond with a RAW JSON ARRAY containing only the questions. Ensure the JSON is completely valid, clean, and has no outer markdown wrappers other than maybe raw text or clean ```json blocks which I will parse.
                    
                    JSON Array Element Schema:
                    {
                      "question": "string representing the question",
                      "options": ["option A", "option B", "option C", "option D"],
                      "answerIndex": integer from 0 to 3 representing the index of the correct option,
                      "explanation": "concise tutorial explanation of why this answer is correct"
                    }
                    
                    Do not add any text before or after the JSON array. Output strictly the valid JSON.
                """.trimIndent()

                val quizJsonStr = if (isApiKeyPlaceholder()) {
                    simulateQuizFallbackJson(topic, title, difficulty, materialContent)
                } else {
                    val request = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = promptText)))),
                        generationConfig = GenerationConfig(
                            responseMimeType = "application/json",
                            temperature = 0.5f
                        )
                    )
                    val result = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                    val raw = RetrofitClient.extractText(result)
                    cleanJson(raw)
                }

                // Verify can be parsed
                val parsedQuestions = parseQuizQuestions(quizJsonStr)
                if (parsedQuestions.isEmpty()) {
                    throw Exception("Could not parse generated quiz JSON format, please retry.")
                }

                val quizId = withContext(Dispatchers.IO) {
                    repository.insertQuiz(
                        StudyQuiz(
                            courseId = courseId,
                            title = title,
                            questionsJson = quizJsonStr,
                            totalQuestions = parsedQuestions.size,
                            score = 0,
                            isCompleted = false
                        )
                    )
                }

                // Load selection
                val savedQuizzes = repository.getQuizzesForCourse(courseId).firstOrNull() ?: emptyList()
                val active = savedQuizzes.find { it.id == quizId.toInt() }
                if (active != null) {
                    startQuizSession(active)
                    AuthManager.syncQuizToFirestore(active)
                }

                _uiEvents.emit("Practice Prep Quiz generated and loaded!")
            } catch (e: Exception) {
                Log.e("StudyGenius", "Error generating practice test", e)
                errorMessage.value = "Failed to compile Quiz: ${e.message}."
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun cleanJson(text: String): String {
        var raw = text.trim()
        if (raw.startsWith("```")) {
            val lines = raw.lines()
            val filteredLines = lines.drop(1).dropLast(1)
            raw = filteredLines.joinToString("\n")
        }
        val firstBracket = raw.indexOf('[')
        val lastBracket = raw.lastIndexOf(']')
        if (firstBracket >= 0 && lastBracket >= 0 && lastBracket > firstBracket) {
            raw = raw.substring(firstBracket, lastBracket + 1)
        }
        return raw.trim()
    }

    private fun simulateQuizFallbackJson(topic: String, title: String = "", difficulty: String = "Medium", materialContent: String? = null): String {
        val topicClean = topic.ifBlank { "the uploaded materials" }
        val sourceText = materialContent ?: ""
        
        // Let's extract some potential key aspects or words
        val keywords = if (sourceText.isNotBlank()) {
            sourceText.split(Regex("\\s+"))
                .map { it.replace(Regex("[^a-zA-Z]"), "") }
                .filter { it.length > 5 }
                .distinct()
                .take(6)
        } else {
            emptyList()
        }

        val kw0 = keywords.getOrNull(0) ?: "system variables"
        val kw1 = keywords.getOrNull(1) ?: "boundary conditions"
        val kw2 = keywords.getOrNull(2) ?: "governing formulas"
        val kw3 = keywords.getOrNull(3) ?: "theoretical matrix"

        val qDiffText = when (difficulty.lowercase()) {
            "easy" -> "Recall fundamental properties of"
            "hard" -> "Analyze complex advanced dynamics and edge constraints of"
            else -> "Apply key concepts and mathematical behaviors of"
        }

        return """
            [
              {
                "question": "[$difficulty Level] $qDiffText '$topicClean': what is the core significance of '$kw0' as highlighted in the source reference?",
                "options": [
                  "It represents an isolated, non-essential variable",
                  "It serves as a primary foundation for applying governing principles and constraints",
                  "It is a minor detail with low scholastic relevance",
                  "It has no direct relationship with boundary conditions"
                ],
                "answerIndex": 1,
                "explanation": "In academic study guidelines under $difficulty difficulty, '$kw0' acts as a critical factor in understanding the structural foundation of '$topicClean'."
              },
              {
                "question": "[$difficulty Level] Which of the following describes the most correct method of evaluating '$kw1' in practical applications of '$topicClean'?",
                "options": [
                  "Ignoring edge cases to solve the core equation sooner",
                  "Checking localized parameters and performing step-by-step verification checks on '$kw2'",
                  "Skipping algebraic checks altogether",
                  "Translating the formulas into random schemas without verification"
                ],
                "answerIndex": 1,
                "explanation": "For rigorous topic exploration of '$topicClean' at a $difficulty level, boundary parameters like '$kw1' and '$kw2' must be calculated sequentially."
              },
              {
                "question": "[$difficulty Level] According to the curated material, how can the integration of '$kw3' with the overall course syllabus of '$topicClean' be best described?",
                "options": [
                  "As a separate, unrelated conceptual track",
                  "As a key element that helps build an intelligent, comprehensive assistant workflow",
                  "As an outdated theoretical construct superseded by search prompts",
                  "As a simple memorization tool with limited long-term retention"
                ],
                "answerIndex": 1,
                "explanation": "A complete $difficulty synthesis requires matching '$kw3' back into the wider concepts framework for full conceptual mastery."
              }
            ]
        """.trimIndent()
    }

    // Parse JSON into Kotlin models
    fun parseQuizQuestions(json: String): List<QuizQuestion> {
        val list = mutableListOf<QuizQuestion>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val question = obj.getString("question")
                val optArr = obj.getJSONArray("options")
                val options = List(optArr.length()) { idx -> optArr.getString(idx) }
                val answerIndex = obj.getInt("answerIndex")
                val explanation = obj.optString("explanation", "Correct!")
                list.add(QuizQuestion(question, options, answerIndex, explanation))
            }
        } catch (e: Exception) {
            Log.e("StudyGenius", "JSON parse error", e)
        }
        return list
    }

    // Start active practice test
    fun startQuizSession(quiz: StudyQuiz) {
        currentSelectedQuiz.value = quiz
        val questions = parseQuizQuestions(quiz.questionsJson)
        quizQuestions.value = questions
        quizCurrentQuestionIndex.value = 0
        quizSelectedOptionIndex.value = null
        quizScore.value = 0
        quizChecked.value = false
        quizShowExplanation.value = false
    }

    // Submit individual answer selection
    fun checkCurrentQuestionAnswer(selectedIndex: Int) {
        quizSelectedOptionIndex.value = selectedIndex
        quizChecked.value = true
        quizShowExplanation.value = true

        val currentQ = quizQuestions.value.getOrNull(quizCurrentQuestionIndex.value)
        if (currentQ != null && selectedIndex == currentQ.answerIndex) {
            quizScore.value += 1
        }
    }

    // Next question in practice test
    fun advanceToNextQuestion() {
        val nextIdx = quizCurrentQuestionIndex.value + 1
        if (nextIdx < quizQuestions.value.size) {
            quizCurrentQuestionIndex.value = nextIdx
            quizSelectedOptionIndex.value = null
            quizChecked.value = false
            quizShowExplanation.value = false
        } else {
            // Quiz completed!
            val active = currentSelectedQuiz.value
            if (active != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val finalScore = quizScore.value
                    val total = quizQuestions.value.size
                    val updated = active.copy(
                        score = finalScore,
                        totalQuestions = total,
                        isCompleted = true
                    )
                    repository.insertQuiz(updated)
                    
                    // Sync the completed quiz results to Firestore
                    AuthManager.syncQuizToFirestore(updated)

                    // Reload active selection
                    withContext(Dispatchers.Main) {
                        currentSelectedQuiz.value = updated
                    }
                    _uiEvents.emit("Quiz completed! Streak recorded! Final score: $finalScore / $total")
                }
            }
        }
    }

    fun deleteMaterial(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMaterial(id)
            if (currentSelectedMaterial.value?.id == id) {
                currentSelectedMaterial.value = null
            }
        }
    }

    fun deleteAssignment(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAssignment(id)
            if (currentSelectedAssignment.value?.id == id) {
                currentSelectedAssignment.value = null
            }
        }
    }

    fun deleteQuiz(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteQuiz(id)
            if (currentSelectedQuiz.value?.id == id) {
                currentSelectedQuiz.value = null
                quizQuestions.value = emptyList()
            }
        }
    }

    // REAL-TIME STUDY COACH CHAT COMPONENT
    fun sendChatMessage(message: String) {
        if (message.isBlank()) return
        val currentCourse = selectedCourse.value ?: return

        val userMsg = ChatMessage(content = message, isUser = true)
        val currentChat = chatHistory.value + userMsg
        chatHistory.value = currentChat

        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val responseText = if (isApiKeyPlaceholder()) {
                    "Study Coach: I am currently in demonstration mode. To get tailored study schedules or concept deep dives through Gemini, make sure to add your GEMINI_API_KEY to the Secrets panel."
                } else {
                    // Set up context
                    val systemPrompt = "You are a friendly, encouraging academic study coach for a Course named '${currentCourse.name}'. " +
                            "Help the student unpack assignment metrics, explain tricky solutions, suggest tailored study calendars, or simplify core chapters."

                    val chatHistoryPrompt = currentChat.takeLast(6).joinToString("\n") {
                        if (it.isUser) "Student: ${it.content}" else "Study Coach: ${it.content}"
                    }

                    val prompt = """
                        The student is asking: "$message"
                        Answer concisely with key educational formatting. Direct them to make flashcards or practice tests as supplementary preps.
                    """.trimIndent()

                    val request = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = chatHistoryPrompt + "\n" + prompt)))),
                        generationConfig = GenerationConfig(temperature = 0.7f),
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                    )
                    val result = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                    RetrofitClient.extractText(result)
                }

                chatHistory.value = chatHistory.value + ChatMessage(content = responseText, isUser = false)
            } catch (e: Exception) {
                Log.e("StudyGenius", "Chat session error", e)
                chatHistory.value = chatHistory.value + ChatMessage(
                    content = "Error: Failed to process message. Check your networks or keys. (${e.message})",
                    isUser = false
                )
            } finally {
                isLoading.value = false
            }
        }
    }

    // GROUNDED CLARIFYING CHAT FOR SELECTED STUDY MATERIAL/DOCUMENT
    fun sendMaterialChatMessage(message: String) {
        if (message.isBlank()) return
        val material = currentSelectedMaterial.value ?: return

        val userMsg = ChatMessage(content = message, isUser = true)
        val currentChat = currentMaterialChatHistory.value + userMsg
        currentMaterialChatHistory.value = currentChat

        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val responseText = if (isApiKeyPlaceholder()) {
                    "Study Coach Mode: I am currently in demonstration mode. To ask clarifying questions grounded in this study guide through the real-time Gemini API, please configure a GEMINI_API_KEY in the Secrets tab."
                } else {
                    val systemPrompt = "You are an expert academic teaching assistant. The student has uploaded a study document titled '${material.title}' and is asking clarifying questions. Answer based on the document's facts first. Be encouraging, structured, and informative. Use clear Markdown bullet points."

                    val chatHistoryPrompt = currentChat.takeLast(6).joinToString("\n") {
                        if (it.isUser) "Student: ${it.content}" else "Assistant: ${it.content}"
                    }

                    val prompt = """
                        Here is the full text of the student's study material:
                        \"\"\"
                        ${material.rawContent}
                        \"\"\"

                        Here is the curated study summary:
                        \"\"\"
                        ${material.summaryText}
                        \"\"\"

                        Please answer the following clarifying question by the student based strictly on the content above. If it's unrelated, politely redirect them.
                        Question: "$message"
                    """.trimIndent()

                    val request = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = chatHistoryPrompt + "\n" + prompt)))),
                        generationConfig = GenerationConfig(temperature = 0.5f),
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                    )
                    val result = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                    RetrofitClient.extractText(result)
                }

                currentMaterialChatHistory.value = currentMaterialChatHistory.value + ChatMessage(content = responseText, isUser = false)
            } catch (e: Exception) {
                Log.e("StudyGenius", "Material-specific chat error", e)
                currentMaterialChatHistory.value = currentMaterialChatHistory.value + ChatMessage(
                    content = "Error: Couldn't obtain a response. Please check your API key or network setup. (${e.localizedMessage})",
                    isUser = false
                )
            } finally {
                isLoading.value = false
            }
        }
    }

    // FLASHCARD PARSING & GENERATION
    fun parseFlashcards(json: String): List<Flashcard> {
        val list = mutableListOf<Flashcard>()
        try {
            val cleanStr = cleanJson(json)
            val arr = JSONArray(cleanStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val term = obj.getString("term")
                val definition = obj.getString("definition")
                list.add(Flashcard(term, definition))
            }
        } catch (e: Exception) {
            Log.e("StudyGenius", "Flashcards JSON parse error", e)
        }
        return list
    }

    fun loadFlashcardsForMaterial(materialId: Int) {
        viewModelScope.launch {
            repository.getFlashcardDeckForMaterial(materialId).collect { deck ->
                currentFlashcardDeck.value = deck
                if (deck != null) {
                    currentMaterialFlashcards.value = parseFlashcards(deck.flashcardsJson)
                } else {
                    currentMaterialFlashcards.value = emptyList()
                    currentFlashcardDeck.value = null
                }
            }
        }
    }

    fun generateFlashcardsForMaterial(courseId: Int, materialId: Int, title: String, content: String) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            try {
                val promptText = """
                    You are an expert academic study assistant. Your task is to analyze the study material provided below and extract the most important key terms and definitions to create a high-quality study flashcard deck.
                    
                    Guidelines:
                    1. Extract between 5 and 12 critical key technical terms, concepts, algorithms, formulas, or definitions found in the context.
                    2. Ensure definitions are accurate, concise (1-2 clear, informative sentences), and directly relevant for exams/memorization.
                    3. Respond with a valid, clean, raw JSON array of objects. Do not include any markdown fences (like ```json) or conversational preamble.
                    
                    JSON Schema to conform to EXACTLY:
                    [
                      {
                        "term": "Term Name",
                        "definition": "Definition explanation"
                      }
                    ]

                    Study Material Title: $title
                    Raw Content to Extract From:
                    $content
                """.trimIndent()

                val flashcardsJsonStr = if (isApiKeyPlaceholder()) {
                    simulateFlashcardsFallback(title, content)
                } else {
                    val request = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = promptText)))),
                        generationConfig = GenerationConfig(
                            responseMimeType = "application/json",
                            temperature = 0.5f
                        )
                    )
                    val result = RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                    val raw = RetrofitClient.extractText(result)
                    cleanJson(raw)
                }

                // Verify can be parsed
                val parsedCards = parseFlashcards(flashcardsJsonStr)
                if (parsedCards.isEmpty()) {
                    throw Exception("Could not parse generated flashcards JSON format, please retry.")
                }

                // Delete previous if any (or replace)
                withContext(Dispatchers.IO) {
                    repository.getFlashcardDeckForMaterial(materialId).firstOrNull()?.let { oldDeck ->
                        repository.deleteFlashcardDeck(oldDeck.id)
                    }
                    val deckId = repository.insertFlashcardDeck(
                        FlashcardDeck(
                            courseId = courseId,
                            materialId = materialId,
                            title = title,
                            flashcardsJson = flashcardsJsonStr
                        )
                    )
                    
                    val savedDecks = repository.getFlashcardDecksForCourse(courseId).firstOrNull() ?: emptyList()
                    val newlyCreatedDeck = savedDecks.find { it.id == deckId.toInt() }
                    if (newlyCreatedDeck != null) {
                        AuthManager.syncFlashcardDeckToFirestore(newlyCreatedDeck)
                    }
                }

                _uiEvents.emit("Terminology flashcards successfully extracted from PDF study document!")
            } catch (e: Exception) {
                Log.e("StudyGenius", "Error generating flashcards", e)
                errorMessage.value = "Failed to extract Flashcards: ${e.message}."
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun simulateFlashcardsFallback(title: String, content: String): String {
        val terms = mutableListOf<Pair<String, String>>()
        
        // Find definitions or sentences containing "is", "means", "refers to"
        val sentences = content.split(Regex("[.!?\n]")).map { it.trim() }.filter { it.length > 20 }
        for (sentence in sentences) {
            if (sentence.contains(" is ", ignoreCase = true) || 
                sentence.contains(" refers to ", ignoreCase = true) || 
                sentence.contains(" means ", ignoreCase = true) ||
                sentence.contains(" defined as ", ignoreCase = true)) {
                
                val parts = if (sentence.contains(" refers to ", ignoreCase = true)) {
                    sentence.split(Regex("(?i) refers to "))
                } else if (sentence.contains(" means ", ignoreCase = true)) {
                    sentence.split(Regex("(?i) means "))
                } else if (sentence.contains(" is defined as ", ignoreCase = true)) {
                    sentence.split(Regex("(?i) is defined as "))
                } else {
                    sentence.split(Regex("(?i) is "))
                }
                
                if (parts.size >= 2) {
                    val termCandidate = parts[0].trim().trim('*', '_', '"', '\'').take(40)
                    val defCandidate = parts[1].trim()
                    if (termCandidate.isNotBlank() && termCandidate.split(" ").size in 1..4 && defCandidate.isNotBlank()) {
                        terms.add(Pair(termCandidate, defCandidate))
                    }
                }
            }
            if (terms.size >= 8) break
        }
        
        // If we didn't find enough terms, add some high-quality contextual definitions based on title
        if (terms.size < 4) {
            val words = title.split(" ").filter { it.length > 3 && it[0].isUpperCase() }
            val keyword1 = words.getOrNull(0) ?: "Core Concept"
            val keyword2 = words.getOrNull(1) ?: "Critical Paradigm"
            
            terms.add(Pair(keyword1, "The fundamental, core thematic element of $title vital for foundational baseline understanding."))
            terms.add(Pair(keyword2, "A secondary essential system structure or parameter discussed in detail throughout the $title lecture course."))
            terms.add(Pair("Memory Architecture", "The structural organization of computer storage subsystems, balancing speed, capacity, and cost parameters."))
            terms.add(Pair("Heuristic Analysis", "A practical approach to problem-solving or self-discovery that employs a practical method not guaranteed to be optimal."))
            terms.add(Pair("Computational Complexity", "A branch of theory of computation focusing on classifying computational problems according to their resource usage during execution."))
        }
        
        val sb = StringBuilder()
        sb.append("[\n")
        terms.forEachIndexed { i, pair ->
            sb.append("  {\n")
            sb.append("    \"term\": \"${pair.first.replace("\"", "\\\"").replace("\n", " ")}\",\n")
            sb.append("    \"definition\": \"${pair.second.replace("\"", "\\\"").replace("\n", " ")}\"\n")
            sb.append("  }")
            if (i < terms.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }
}

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String
)

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
