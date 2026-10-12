/*
 * Copyright (C) 2026 CodeOwl contributors
 *
 * This file is part of CodeOwl.
 *
 * CodeOwl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * CodeOwl is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.se2.domain.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tripwire for the bug class that cost us the distractors, the context blocks and the AP31
 * split plans: a copy routine that names fields one by one and silently stops being complete
 * the moment somebody adds a column.
 *
 * <p>The test does not carry a list of fields that must be copied — such a list would go stale
 * exactly as quietly as the copy routine did. It reflects over every declared field instead,
 * fills each one with a value distinguishable from its default, copies, and demands that every
 * field came across. Only the handful that must <em>not</em> survive is enumerated, in
 * {@code RESET_BY_*} below.
 *
 * <p>So a new field on either DTO is copied by default, and the only way to exclude one is to
 * name it here — a visible, reviewable line in a diff, rather than an omission nobody sees.
 * When this test fails after you added a field, the question to answer is: should a copy of
 * this task carry it? Yes means you are already done; no means add it to the reset set with a
 * comment saying why.
 */
class CloneCompletenessTest {

    /**
     * Fields a task copy must deliberately drop.
     *
     * <p>{@code id} because a copy is a new row, {@code sequenceId} because the copy belongs to
     * a different parent — and that parent is the argument to {@code copyFor}.
     */
    private static final Set<String> RESET_BY_TASK_COPY = Set.of("id", "sequenceId");

    /**
     * Fields a sequence copy must deliberately drop.
     *
     * <p>{@code id} and {@code ownerId} for the same reasons as above, {@code createdAt} because
     * the copy is new and the database stamps it, and {@code tasks} because the caller refills
     * it with task copies — leaving it would have both sequences pointing at the same task
     * objects. {@code divergedAt} because a copy starts out unedited.
     */
    private static final Set<String> RESET_BY_SEQUENCE_COPY =
            Set.of("id", "ownerId", "createdAt", "tasks",
                   // copiedFrom is not carried over but rewritten: the copy came from the
                   // sequence being copied, not from wherever that one came from.
                   "copiedFrom",
                   // divergedAt belongs to the sequence that was edited, not to a copy of it.
                   // A fresh copy has been edited by nobody, however much its source had been;
                   // carrying it would list an untouched clone in the library immediately,
                   // which is the rule it exists to enforce, inverted.
                   "divergedAt");

    @Test
    @DisplayName("a task copy carries every field except the two that identify it")
    void taskCopyCarriesEveryFieldExceptIdentity() throws Exception {
        TaskDTO source = new TaskDTO();
        populateEveryField(source, TaskDTO.class);

        UUID newSequenceId = UUID.randomUUID();
        TaskDTO copy = source.copyFor(newSequenceId);

        assertFieldsSurvived(TaskDTO.class, source, copy, RESET_BY_TASK_COPY);
        assertNull(copy.getId(), "a copy is a new row and must not inherit the source id");
        assertEquals(newSequenceId, copy.getSequenceId(), "the copy belongs to its new sequence");
    }

    @Test
    @DisplayName("a sequence copy carries every field except identity, timestamp and task list")
    void sequenceCopyCarriesEveryFieldExceptIdentity() throws Exception {
        TieredTaskSequenceDTO source = new TieredTaskSequenceDTO();
        populateEveryField(source, TieredTaskSequenceDTO.class);

        UUID newOwnerId = UUID.randomUUID();
        TieredTaskSequenceDTO copy = source.copyFor(newOwnerId);

        assertFieldsSurvived(TieredTaskSequenceDTO.class, source, copy, RESET_BY_SEQUENCE_COPY);
        assertNull(copy.getId(), "a copy is a new row and must not inherit the source id");
        assertNull(copy.getCreatedAt(), "the database stamps the copy's creation time");
        assertEquals(newOwnerId, copy.getOwnerId(), "the copy belongs to whoever made it");
        assertTrue(copy.getTasks().isEmpty(), "the caller refills the task list with copies");
        assertEquals(source.getId(), copy.getCopiedFrom(),
                "provenance points at the immediate source, not at the source's own origin");
    }

    @Test
    @DisplayName("the sequence copy does not share its concept list with the source")
    void sequenceCopyDoesNotAliasConcepts() {
        TieredTaskSequenceDTO source = TieredTaskSequenceDTO.builder()
                .concepts(new ArrayList<>(List.of("recursion")))
                .build();

        TieredTaskSequenceDTO copy = source.copyFor(UUID.randomUUID());
        copy.getConcepts().add("loops");

        assertEquals(List.of("recursion"), source.getConcepts(),
                "editing the copy's concepts must not reach back into the source");
    }

    @Test
    @DisplayName("copying leaves the source untouched")
    void copyingDoesNotMutateTheSource() throws Exception {
        TaskDTO source = new TaskDTO();
        populateEveryField(source, TaskDTO.class);
        UUID originalId = source.getId();
        UUID originalSequenceId = source.getSequenceId();

        source.copyFor(UUID.randomUUID());

        assertEquals(originalId, source.getId());
        assertEquals(originalSequenceId, source.getSequenceId());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Reflection helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Asserts that every declared field either survived the copy or is named in {@code reset}.
     *
     * <p>The loop is over the class's actual fields, not over a list in this file — that is the
     * whole mechanism. A field added tomorrow is checked tomorrow, without anyone remembering
     * to come back here.
     */
    private static <T> void assertFieldsSurvived(Class<T> type, T source, T copy, Set<String> reset)
            throws Exception {
        for (Field field : copyableFields(type)) {
            field.setAccessible(true);
            Object before = field.get(source);
            Object after = field.get(copy);

            if (reset.contains(field.getName())) {
                continue; // asserted individually in the calling test, where the intent is readable
            }

            assertEquals(before, after,
                    "field '" + field.getName() + "' was lost by " + type.getSimpleName()
                            + ".copyFor(). Either carry it over, or add it to the reset set in "
                            + CloneCompletenessTest.class.getSimpleName() + " with a reason.");
        }
    }

    /** Every declared instance field; Lombok's and JaCoCo's synthetic members are not ours to copy. */
    private static List<Field> copyableFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic()) continue;
            if (Modifier.isStatic(field.getModifiers())) continue;
            fields.add(field);
        }
        return fields;
    }

    /**
     * Fills every field with a value that differs from what the field would hold by default.
     *
     * <p>The "differs from default" part is what gives the test teeth. Lombok's
     * {@code @Builder.Default} puts {@code tierCount = 3}, {@code targetLanguage = "de"} and
     * {@code distractorsEnabled = true} in place whenever the builder is not told otherwise —
     * which is precisely how a five-tier sequence used to come back as a three-tier one without
     * anything looking broken. Seeding those fields with something else means a dropped field
     * shows up as a failed assertion instead of a plausible-looking default.
     */
    private static void populateEveryField(Object target, Class<?> type) throws Exception {
        int seed = 0;
        for (Field field : copyableFields(type)) {
            field.setAccessible(true);
            field.set(target, distinctValue(field.getType(), field.getName(), seed++));
        }
    }

    private static Object distinctValue(Class<?> type, String fieldName, int seed) {
        if (type == String.class) return "value-of-" + fieldName;
        if (type == UUID.class) return UUID.randomUUID();
        if (type == int.class || type == Integer.class) return 40 + seed;
        if (type == long.class || type == Long.class) return 40L + seed;
        if (type == float.class || type == Float.class) return 0.5f + seed;
        if (type == double.class || type == Double.class) return 0.5d + seed;
        // false, not true: the @Builder.Default on distractorsEnabled is true, so only false
        // can prove the flag was actually carried over rather than re-defaulted.
        if (type == boolean.class || type == Boolean.class) return Boolean.FALSE;
        if (type == LocalDateTime.class) return LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        if (List.class.isAssignableFrom(type)) return new ArrayList<>(List.of("element-" + fieldName));
        return instantiate(type, fieldName);
    }

    /**
     * A real instance of an arbitrary field type.
     *
     * <p>Mocks would read more simply here, but they are not an option: {@code SolveContract} is
     * a record and {@code GenerationConstraints} is final, and on a current JVM Mockito can
     * redefine neither. Constructing the real thing is also the more honest test — it exercises
     * whatever validation the type imposes on itself.
     *
     * <p>Records go through their canonical constructor with generated components, everything
     * else through a no-argument constructor. A type that offers neither falls back to a mock,
     * and if that fails too the test fails — which is the right outcome, because it means
     * somebody added a field whose participation in a copy nobody has thought about yet.
     */
    private static Object instantiate(Class<?> type, String fieldName) {
        try {
            if (type.isRecord()) {
                RecordComponent[] components = type.getRecordComponents();
                Class<?>[] parameterTypes = new Class<?>[components.length];
                Object[] arguments = new Object[components.length];
                for (int i = 0; i < components.length; i++) {
                    parameterTypes[i] = components[i].getType();
                    arguments[i] = distinctValue(
                            parameterTypes[i], fieldName + "-" + components[i].getName(), i);
                }
                Constructor<?> canonical = type.getDeclaredConstructor(parameterTypes);
                canonical.setAccessible(true);
                return canonical.newInstance(arguments);
            }
            Constructor<?> noArgument = type.getDeclaredConstructor();
            noArgument.setAccessible(true);
            return noArgument.newInstance();
        } catch (ReflectiveOperationException e) {
            return Mockito.mock(type);
        }
    }
}
