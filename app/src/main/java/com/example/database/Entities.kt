package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "course_materials")
data class CourseMaterial(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseId: Int,
    val title: String,
    val rawContent: String,
    val summaryText: String,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "assignment_solutions")
data class AssignmentSolution(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseId: Int,
    val title: String,
    val problemsText: String,
    val instructions: String,
    val solutionsDocumentText: String,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_quizzes")
data class StudyQuiz(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseId: Int,
    val title: String,
    val questionsJson: String, // JSON representation of a list of questions
    val score: Int = 0,
    val totalQuestions: Int = 0,
    val isCompleted: Boolean = false,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "flashcard_decks")
data class FlashcardDeck(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseId: Int,
    val materialId: Int,
    val title: String,
    val flashcardsJson: String, // JSON representation of key terms and definitions
    val createdTimestamp: Long = System.currentTimeMillis()
)

data class Flashcard(
    val term: String,
    val definition: String
)

