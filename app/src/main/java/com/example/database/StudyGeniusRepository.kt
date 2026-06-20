package com.example.database

import kotlinx.coroutines.flow.Flow

class StudyGeniusRepository(
    private val courseDao: CourseDao,
    private val materialDao: MaterialDao,
    private val assignmentDao: AssignmentDao,
    private val quizDao: QuizDao,
    private val flashcardDao: FlashcardDao
) {
    val allCourses: Flow<List<Course>> = courseDao.getAllCourses()
    val allMaterials: Flow<List<CourseMaterial>> = materialDao.getAllMaterials()
    val allAssignments: Flow<List<AssignmentSolution>> = assignmentDao.getAllAssignments()
    val allQuizzes: Flow<List<StudyQuiz>> = quizDao.getAllQuizzes()
    val allFlashcardDecks: Flow<List<FlashcardDeck>> = flashcardDao.getAllFlashcardDecks()

    fun getMaterialsForCourse(courseId: Int): Flow<List<CourseMaterial>> =
        materialDao.getMaterialsForCourse(courseId)

    fun getAssignmentsForCourse(courseId: Int): Flow<List<AssignmentSolution>> =
        assignmentDao.getAssignmentsForCourse(courseId)

    fun getQuizzesForCourse(courseId: Int): Flow<List<StudyQuiz>> =
        quizDao.getQuizzesForCourse(courseId)

    fun getFlashcardDecksForCourse(courseId: Int): Flow<List<FlashcardDeck>> =
        flashcardDao.getFlashcardDecksForCourse(courseId)

    fun getFlashcardDeckForMaterial(materialId: Int): Flow<FlashcardDeck?> =
        flashcardDao.getFlashcardDeckForMaterial(materialId)

    suspend fun insertCourse(course: Course): Int {
        return courseDao.insertCourse(course).toInt()
    }

    suspend fun deleteCourse(id: Int) {
        courseDao.deleteCourseById(id)
    }

    suspend fun insertMaterial(material: CourseMaterial): Int {
        return materialDao.insertMaterial(material).toInt()
    }

    suspend fun deleteMaterial(id: Int) {
        materialDao.deleteMaterialById(id)
    }

    suspend fun insertAssignment(assignment: AssignmentSolution): Int {
        return assignmentDao.insertAssignment(assignment).toInt()
    }

    suspend fun deleteAssignment(id: Int) {
        assignmentDao.deleteAssignmentById(id)
    }

    suspend fun insertQuiz(quiz: StudyQuiz): Int {
        return quizDao.insertQuiz(quiz).toInt()
    }

    suspend fun deleteQuiz(id: Int) {
        quizDao.deleteQuizById(id)
    }

    suspend fun insertFlashcardDeck(deck: FlashcardDeck): Int {
        return flashcardDao.insertFlashcardDeck(deck).toInt()
    }

    suspend fun deleteFlashcardDeck(id: Int) {
        flashcardDao.deleteFlashcardDeckById(id)
    }
}
