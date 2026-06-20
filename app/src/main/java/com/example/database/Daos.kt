package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY createdTimestamp DESC")
    fun getAllCourses(): Flow<List<Course>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: Course): Long

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourseById(id: Int)
}

@Dao
interface MaterialDao {
    @Query("SELECT * FROM course_materials WHERE courseId = :courseId ORDER BY createdTimestamp DESC")
    fun getMaterialsForCourse(courseId: Int): Flow<List<CourseMaterial>>

    @Query("SELECT * FROM course_materials ORDER BY createdTimestamp DESC")
    fun getAllMaterials(): Flow<List<CourseMaterial>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: CourseMaterial): Long

    @Query("DELETE FROM course_materials WHERE id = :id")
    suspend fun deleteMaterialById(id: Int)
}

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignment_solutions WHERE courseId = :courseId ORDER BY createdTimestamp DESC")
    fun getAssignmentsForCourse(courseId: Int): Flow<List<AssignmentSolution>>

    @Query("SELECT * FROM assignment_solutions ORDER BY createdTimestamp DESC")
    fun getAllAssignments(): Flow<List<AssignmentSolution>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignment(assignment: AssignmentSolution): Long

    @Query("DELETE FROM assignment_solutions WHERE id = :id")
    suspend fun deleteAssignmentById(id: Int)
}

@Dao
interface QuizDao {
    @Query("SELECT * FROM study_quizzes WHERE courseId = :courseId ORDER BY createdTimestamp DESC")
    fun getQuizzesForCourse(courseId: Int): Flow<List<StudyQuiz>>

    @Query("SELECT * FROM study_quizzes ORDER BY createdTimestamp DESC")
    fun getAllQuizzes(): Flow<List<StudyQuiz>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: StudyQuiz): Long

    @Query("DELETE FROM study_quizzes WHERE id = :id")
    suspend fun deleteQuizById(id: Int)
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcard_decks WHERE courseId = :courseId ORDER BY createdTimestamp DESC")
    fun getFlashcardDecksForCourse(courseId: Int): Flow<List<FlashcardDeck>>

    @Query("SELECT * FROM flashcard_decks WHERE materialId = :materialId LIMIT 1")
    fun getFlashcardDeckForMaterial(materialId: Int): Flow<FlashcardDeck?>

    @Query("SELECT * FROM flashcard_decks ORDER BY createdTimestamp DESC")
    fun getAllFlashcardDecks(): Flow<List<FlashcardDeck>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcardDeck(deck: FlashcardDeck): Long

    @Query("DELETE FROM flashcard_decks WHERE id = :id")
    suspend fun deleteFlashcardDeckById(id: Int)
}

