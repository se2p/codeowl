-- Reverses seed-data/pre-filled-content.sql: removes every row that file
-- inserts, in the reverse of its own FK-dependency order, so a database it
-- was applied to can go back to schema-only.
--
-- Unlike migration/undo scripts this is not paired with a flyway_schema_history
-- entry -- pre-filled-content.sql was never a migration, so there is nothing
-- to delete from that table either. Run this only against a database where
-- pre-filled-content.sql is the sole source of rows in these tables; on one
-- that has since accumulated real data of its own in the same tables, this
-- deletes that too; there is no way to tell the two apart after the fact.

DELETE FROM public.task_change_analyses;
DELETE FROM public.task_originals;
DELETE FROM public.learning_events;
DELETE FROM public.hint_usage;
DELETE FROM public.worksheets_sequence;
DELETE FROM public.class_worksheets;
DELETE FROM public.tiered_task_sequence_concepts;
DELETE FROM public.tasks;
DELETE FROM public.sequence_tags;
DELETE FROM public.class_students;
DELETE FROM public.solve_code_generations;
DELETE FROM public.problem_statement_ratings;
DELETE FROM public.worksheets;
DELETE FROM public.tiered_task_sequences;
DELETE FROM public.classes;
DELETE FROM public.auth_tokens;
DELETE FROM public.snippet_sources;
DELETE FROM public.snippet_concept;
DELETE FROM public.problem_statement_generations;
DELETE FROM public.eval_result;
DELETE FROM public.solve_eval_run_metrics;
DELETE FROM public.users;
DELETE FROM public.snippets;
DELETE FROM public.prompt_templates;
DELETE FROM public.feedback_logs;
DELETE FROM public.eval_run;
DELETE FROM public.eval_input;
