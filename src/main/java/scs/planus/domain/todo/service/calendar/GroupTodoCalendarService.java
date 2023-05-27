package scs.planus.domain.todo.service.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import scs.planus.domain.group.entity.GroupMember;
import scs.planus.domain.group.repository.GroupMemberRepository;
import scs.planus.domain.group.repository.GroupRepository;
import scs.planus.domain.todo.dto.calendar.TodoDailyDto;
import scs.planus.domain.todo.dto.calendar.TodoDailyResponseDto;
import scs.planus.domain.todo.dto.calendar.TodoPeriodResponseDto;
import scs.planus.domain.todo.entity.GroupTodo;
import scs.planus.domain.todo.entity.GroupTodoCompletion;
import scs.planus.domain.todo.entity.Todo;
import scs.planus.domain.todo.repository.GroupTodoCompletionRepository;
import scs.planus.domain.todo.repository.TodoQueryRepository;
import scs.planus.global.exception.PlanusException;
import scs.planus.global.util.validator.Validator;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static scs.planus.global.exception.CustomExceptionStatus.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class GroupTodoCalendarService {

    private final TodoQueryRepository todoQueryRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupTodoCompletionRepository groupTodoCompletionRepository;

    public List<TodoPeriodResponseDto> getPeriodGroupTodos(Long memberId, Long groupId, LocalDate from, LocalDate to) {
        GroupMember groupMember = groupMemberRepository.findByMemberIdAndGroupId(memberId, groupId)
                .orElseThrow(() -> {
                    groupRepository.findById(groupId)
                            .orElseThrow(() -> new PlanusException(NOT_EXIST_GROUP));
                    return new PlanusException(NOT_JOINED_GROUP);
                });

        Validator.validateStartDateBeforeEndDate(from, to);

        List<GroupTodo> todos = todoQueryRepository.findPeriodGroupTodosByDate(groupId, from, to);
        List<TodoPeriodResponseDto> responseDtos = todos.stream()
                .map(TodoPeriodResponseDto::of)
                .collect(Collectors.toList());
        return responseDtos;
    }

    public TodoDailyResponseDto getDailyGroupTodos(Long memberId, Long groupId, LocalDate date) {
        GroupMember groupMember = groupMemberRepository.findByMemberIdAndGroupId(memberId, groupId)
                .orElseThrow(() -> {
                    groupRepository.findById(groupId)
                            .orElseThrow(() -> new PlanusException(NOT_EXIST_GROUP));
                    return new PlanusException(NOT_JOINED_GROUP);
                });

        List<GroupTodo> todos = todoQueryRepository.findDailyGroupTodosByDate(groupId, date);
        List<TodoDailyDto> todoDailyScheduleDtos = getDailyGroupSchedules(todos);
        List<TodoDailyDto> todoDailyDtos = getDailyGroupTodos(todos);

        return TodoDailyResponseDto.of(todoDailyScheduleDtos, todoDailyDtos);
    }

    public List<TodoPeriodResponseDto> getGroupMemberPeriodTodos(Long loginId, Long groupId, Long memberId,
                                                                  LocalDate from, LocalDate to) {
        GroupMember loginGroupMember = groupMemberRepository.findByMemberIdAndGroupId(loginId, groupId)
                .orElseThrow(() -> {
                    groupRepository.findById(groupId)
                            .orElseThrow(() -> new PlanusException(NOT_EXIST_GROUP));
                    return new PlanusException(NOT_JOINED_GROUP);
                });

        GroupMember groupMember = groupMemberRepository.findByMemberIdAndGroupId(memberId, groupId)
                .orElseThrow(() -> new PlanusException(NOT_JOINED_MEMBER_IN_GROUP));

        Validator.validateStartDateBeforeEndDate(from, to);
        List<Todo> todos = todoQueryRepository.findGroupMemberPeriodTodosByDate(memberId, groupId, from, to);

        List<TodoPeriodResponseDto> responseDtos = todos.stream()
                .map(TodoPeriodResponseDto::of)
                .collect(Collectors.toList());
        return responseDtos;
    }

    // TODO 리팩토링 절실..
    public TodoDailyResponseDto getGroupMemberDailyTodos(Long loginId, Long groupId, Long memberId, LocalDate date) {
        GroupMember loginGroupMember = groupMemberRepository.findByMemberIdAndGroupId(loginId, groupId)
                .orElseThrow(() -> {
                    groupRepository.findById(groupId)
                            .orElseThrow(() -> new PlanusException(NOT_EXIST_GROUP));
                    return new PlanusException(NOT_JOINED_GROUP);
                });

        GroupMember groupMember = groupMemberRepository.findByMemberIdAndGroupId(memberId, groupId)
                .orElseThrow(() -> new PlanusException(NOT_JOINED_MEMBER_IN_GROUP));

        List<Todo> todos = todoQueryRepository.findGroupMemberDailyTodosByDate(memberId, groupId, date);

        List<GroupTodo> groupTodos = todos.stream()
                .filter(Todo::isGroupTodo)
                .map(todo -> (GroupTodo) todo)
                .collect(Collectors.toList());

        List<GroupTodoCompletion> groupTodoCompletions = groupTodoCompletionRepository.findByMemberIdAndInGroupTodos(memberId, groupTodos);

        todos.removeAll(groupTodos);

        List<TodoDailyDto> dailyGroupMemberSchedules = getDailyGroupSchedules(groupTodos, groupTodoCompletions);
        List<TodoDailyDto> dailyGroupMemberTodos = getDailyGroupTodos(groupTodos, groupTodoCompletions);

        List<TodoDailyDto> dailySchedules = getDailySchedules(todos);
        List<TodoDailyDto> dailyTodos = getDailyTodos(todos);

        dailySchedules.addAll(dailyGroupMemberSchedules);
        dailyTodos.addAll(dailyGroupMemberTodos);

        return TodoDailyResponseDto.of(dailySchedules, dailyTodos);
    }

    private List<TodoDailyDto> getDailyGroupSchedules(List<GroupTodo> todos) {
        return todos.stream()
                .filter(todo -> todo.getStartTime() != null)
                .map(TodoDailyDto::ofGroupTodo)
                .collect(Collectors.toList());
    }

    private List<TodoDailyDto> getDailyGroupSchedules(List<GroupTodo> todos, List<GroupTodoCompletion> groupTodoCompletions) {
        return todos.stream()
                .filter(todo -> todo.getStartTime() != null)
                .map(todo -> {
                    GroupTodoCompletion todoCompletion = groupTodoCompletions.stream()
                            .filter(gtc -> gtc.getGroupTodo().equals(todo))
                            .findFirst().orElseThrow(() -> new PlanusException(NOT_EXIST_GROUP_TODO));
                    return TodoDailyDto.ofGroupTodo(todo, todoCompletion);
                })
                .collect(Collectors.toList());
    }

    private List<TodoDailyDto> getDailyGroupTodos(List<GroupTodo> todos) {
        return todos.stream()
                .filter(todo -> todo.getStartTime() == null)
                .map(TodoDailyDto::ofGroupTodo)
                .collect(Collectors.toList());
    }

    private List<TodoDailyDto> getDailyGroupTodos(List<GroupTodo> todos, List<GroupTodoCompletion> groupTodoCompletions) {
        return todos.stream()
                .filter(todo -> todo.getStartTime() == null)
                .map(todo -> {
                    GroupTodoCompletion todoCompletion = groupTodoCompletions.stream()
                            .filter(gtc -> gtc.getGroupTodo().equals(todo))
                            .findFirst().orElseThrow(() -> new PlanusException(NOT_EXIST_GROUP_TODO));
                    return TodoDailyDto.ofGroupTodo(todo, todoCompletion);
                })
                .collect(Collectors.toList());
    }

    private List<TodoDailyDto> getDailySchedules(List<Todo> todos) {
        return todos.stream()
                .filter(todo -> todo.getStartTime() != null)
                .map(TodoDailyDto::of)
                .collect(Collectors.toList());
    }

    private List<TodoDailyDto> getDailyTodos(List<Todo> todos) {
        return todos.stream()
                .filter(todo -> todo.getStartTime() == null)
                .map(TodoDailyDto::of)
                .collect(Collectors.toList());
    }
}
