package com.leslie.teachtrack.student;

import com.leslie.teachtrack.common.ApiException;
import com.leslie.teachtrack.common.PageResponse;
import com.leslie.teachtrack.course.CourseSessionRow;
import com.leslie.teachtrack.course.CourseSessionRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/students")
public class StudentController {
    private final StudentRepository repository;
    private final CourseSessionRepository courseRepository;

    public StudentController(StudentRepository repository, CourseSessionRepository courseRepository) {
        this.repository = repository;
        this.courseRepository = courseRepository;
    }

    @GetMapping
    public PageResponse<StudentRow> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safeOffset = Math.max(0, offset);
        List<StudentRow> items = repository.list(status, query, safeLimit, safeOffset);
        return new PageResponse<>(items, safeLimit, safeOffset, repository.count(status, query));
    }

    @GetMapping("/{id}")
    public StudentRow get(@PathVariable String id) {
        return repository.find(id).orElseThrow(() -> ApiException.notFound("学生不存在: " + id));
    }

    @GetMapping("/{id}/sessions")
    public List<CourseSessionRow> sessions(
            @PathVariable String id,
            @RequestParam(defaultValue = "100") int limit
    ) {
        StudentRow student = repository.find(id)
                .orElseThrow(() -> ApiException.notFound("学生不存在: " + id));
        return courseRepository.findByStudent(student.id(), Math.max(1, Math.min(limit, 500)));
    }
}
