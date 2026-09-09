package com.leslie.teachtrack

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.leslie.teachtrack.core.database.TeachTrackDatabase
import com.leslie.teachtrack.core.model.KnowledgePointEntity
import com.leslie.teachtrack.core.model.StudentEntity
import com.leslie.teachtrack.core.model.StudentKnowledgeEventEntity
import com.leslie.teachtrack.core.model.StudentKnowledgeProgressEntity
import com.leslie.teachtrack.core.model.CourseSessionEntity
import com.leslie.teachtrack.core.model.StudentStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TeachTrackDatabaseInstrumentedTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), TeachTrackDatabase::class.java
    ).allowMainThreadQueries().build()

    @After fun closeDb() = db.close()

    @Test fun studentAndKnowledgeHistoryRoundTrip() = runBlocking {
        val dao = db.dao()
        val studentId = UUID.randomUUID().toString()
        val pointId = UUID.randomUUID().toString()
        dao.upsertStudent(StudentEntity(studentId, "测试学生"))
        dao.upsertKnowledge(KnowledgePointEntity(pointId, name = "动态电路"))
        dao.upsertProgress(StudentKnowledgeProgressEntity(studentId, pointId, level = 2))
        dao.insertKnowledgeEvent(StudentKnowledgeEventEntity(UUID.randomUUID().toString(), studentId, pointId, level = 2))
        assertEquals(1, dao.allStudents().size)
        assertEquals(1, dao.allKnowledge().size)
        assertEquals(1, dao.allProgress().size)
        assertEquals(1, dao.allKnowledgeEvents().size)
    }

    @Test fun hundredStudentsWithTwoHundredCoursesEachStayQueryable() = runBlocking {
        val dao = db.dao()
        val students = (0 until 100).map { index ->
            StudentEntity("student-$index", "Student $index")
        }
        dao.upsertStudents(students)
        val courses = students.flatMap { student ->
            (0 until 200).map { index ->
                CourseSessionEntity(
                    id = "${student.id}-course-$index",
                    studentId = student.id,
                    date = "2026-09-${(index % 28 + 1).toString().padStart(2, '0')}",
                    title = "lesson $index"
                )
            }
        }
        val start = System.nanoTime()
        dao.upsertCourses(courses)
        val loaded = dao.allCourses()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(20_000, loaded.size)
        check(elapsedMs < 10_000) { "20,000 course round trip took ${elapsedMs}ms" }
    }

    @Test fun archiveKeepsStudentHistoryAndHidesActiveListEntry() = runBlocking {
        val dao = db.dao()
        val studentId = UUID.randomUUID().toString()
        dao.upsertStudent(StudentEntity(studentId, "可归档学生"))
        dao.upsertCourse(CourseSessionEntity(UUID.randomUUID().toString(), studentId, "2026-09-09", title = "历史课程"))
        dao.upsertStudent(StudentEntity(studentId, "可归档学生", status = StudentStatus.ARCHIVED))
        assertEquals(StudentStatus.ARCHIVED, dao.observeStudentOnce(studentId)?.status)
        assertEquals(1, dao.observeCourses(studentId).first().size)
        assertEquals(0, dao.observeActiveCount().first())
    }
}
