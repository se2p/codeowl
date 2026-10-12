--
-- PostgreSQL database dump
--

-- Dumped from database version 17.9
-- Dumped by pg_dump version 17.9

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- Name: token_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.token_type AS ENUM (
    'EMAIL_VERIFICATION',
    'PASSWORD_RESET',
    'STUDENT_INVITE'
);


--
-- Name: user_role; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.user_role AS ENUM (
    'ADMIN',
    'TEACHER',
    'STUDENT'
);


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: auth_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.auth_tokens (
    token_id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token_value character varying(64) NOT NULL,
    token_type public.token_type NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    used boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: class_students; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.class_students (
    class_id uuid NOT NULL,
    student_id uuid NOT NULL,
    joined_at timestamp without time zone DEFAULT now()
);


--
-- Name: class_worksheets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.class_worksheets (
    class_id uuid NOT NULL,
    worksheet_id uuid NOT NULL,
    assigned_at timestamp without time zone DEFAULT now()
);


--
-- Name: classes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.classes (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(200) NOT NULL,
    description text DEFAULT ''::text,
    owner_id uuid NOT NULL,
    tracking_enabled boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: concept_curriculum_topic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.concept_curriculum_topic (
    concept_id uuid NOT NULL,
    topic_id uuid NOT NULL
);


--
-- Name: concepts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.concepts (
    id uuid NOT NULL,
    name character varying NOT NULL,
    description text,
    generated boolean DEFAULT false NOT NULL
);


--
-- Name: curriculum_code_examples; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.curriculum_code_examples (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    topic_id uuid NOT NULL,
    language character varying(50) NOT NULL,
    title character varying(255) NOT NULL,
    code text NOT NULL,
    problem_statement text,
    difficulty character varying(20) DEFAULT 'medium'::character varying,
    notes text,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: curriculum_topics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.curriculum_topics (
    id uuid NOT NULL,
    name character varying NOT NULL,
    description text,
    grade_level integer,
    track character varying(30) DEFAULT 'NTG'::character varying,
    learning_area character varying(255),
    parent_id uuid,
    sort_order integer DEFAULT 0
);


--
-- Name: eval_input; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.eval_input (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    snippet_name text NOT NULL,
    language text NOT NULL,
    code text NOT NULL,
    problem_statement text NOT NULL,
    concept text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: eval_result; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.eval_result (
    run_id uuid NOT NULL,
    snippet_name character varying(50) NOT NULL,
    y_true text[] NOT NULL,
    y_pred text[] NOT NULL
);


--
-- Name: eval_run; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.eval_run (
    id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    model text NOT NULL,
    prompt_version text NOT NULL,
    dataset_version integer NOT NULL,
    notes text
);


--
-- Name: feedback_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feedback_logs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    task_id uuid,
    correct boolean,
    user_id uuid,
    class_id uuid,
    clicked_at timestamp without time zone DEFAULT now()
);


--
-- Name: hint_usage; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.hint_usage (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid,
    task_id uuid NOT NULL,
    hint_level integer NOT NULL,
    block_order_at_request jsonb DEFAULT '[]'::jsonb,
    findings jsonb DEFAULT '[]'::jsonb,
    hint_text text,
    llm_explanation text,
    affected_block_ids jsonb DEFAULT '[]'::jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT hint_usage_hint_level_check CHECK (((hint_level >= 1) AND (hint_level <= 2)))
);


--
-- Name: learning_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.learning_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid,
    task_id uuid NOT NULL,
    sequence_id uuid,
    tier_level integer,
    event_type character varying(50) NOT NULL,
    event_data jsonb DEFAULT '{}'::jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: problem_statement_generations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.problem_statement_generations (
    id uuid NOT NULL,
    snippet_name character varying(50) NOT NULL,
    prompt_id uuid NOT NULL,
    model text NOT NULL,
    temperature numeric(4,3),
    top_p numeric(4,3),
    seed integer,
    params jsonb,
    statement_text text NOT NULL,
    raw_response jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: problem_statement_ratings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.problem_statement_ratings (
    id uuid NOT NULL,
    generation_id uuid NOT NULL,
    rated_at timestamp with time zone DEFAULT now() NOT NULL,
    clarity smallint NOT NULL,
    concept_align boolean NOT NULL,
    curriculum_fit smallint NOT NULL,
    appropriateness smallint NOT NULL,
    motivation smallint NOT NULL,
    form_length boolean NOT NULL,
    notes text,
    total_raw smallint GENERATED ALWAYS AS ((((((clarity +
CASE
    WHEN concept_align THEN 1
    ELSE 0
END) + curriculum_fit) + appropriateness) + motivation) +
CASE
    WHEN form_length THEN 1
    ELSE 0
END)) STORED,
    CONSTRAINT problem_statement_ratings_appropriateness_check CHECK (((appropriateness >= 1) AND (appropriateness <= 5))),
    CONSTRAINT problem_statement_ratings_clarity_check CHECK (((clarity >= 1) AND (clarity <= 5))),
    CONSTRAINT problem_statement_ratings_curriculum_fit_check CHECK (((curriculum_fit >= 1) AND (curriculum_fit <= 5))),
    CONSTRAINT problem_statement_ratings_motivation_check CHECK (((motivation >= 1) AND (motivation <= 5)))
);


--
-- Name: prompt_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.prompt_templates (
    id uuid NOT NULL,
    label character varying(50) NOT NULL,
    body text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    notes text,
    system_prompt text
);


--
-- Name: sequence_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sequence_tags (
    sequence_id uuid NOT NULL,
    tag_name character varying(64) NOT NULL,
    source character varying(16) DEFAULT 'auto'::character varying NOT NULL,
    confidence real,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT chk_sequence_tags_source CHECK (((source)::text = ANY ((ARRAY['auto'::character varying, 'llm'::character varying, 'manual'::character varying])::text[])))
);


--
-- Name: snippet_concept; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.snippet_concept (
    snippet_name character varying(50) NOT NULL,
    concept_id uuid NOT NULL
);


--
-- Name: snippet_sources; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.snippet_sources (
    snippet_name character varying(50) NOT NULL,
    repo_url text NOT NULL,
    repo_commit text DEFAULT ''::text NOT NULL,
    repo_path text,
    license text
);


--
-- Name: snippets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.snippets (
    name character varying(50) NOT NULL,
    language character varying(20) NOT NULL,
    code text NOT NULL,
    version integer DEFAULT 1 NOT NULL
);


--
-- Name: solve_code_generations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solve_code_generations (
    id uuid NOT NULL,
    run_id uuid NOT NULL,
    snippet_name character varying(50) NOT NULL,
    ps_generation_id uuid,
    model text NOT NULL,
    temperature numeric(4,3),
    top_p numeric(4,3),
    seed integer,
    raw_response jsonb,
    code_text text NOT NULL,
    compiled boolean DEFAULT false NOT NULL,
    exit_code integer,
    stdout text,
    stderr text,
    verdict text NOT NULL,
    comparator_mode text,
    tests_total integer DEFAULT 1 NOT NULL,
    tests_passed integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT solve_code_generations_comparator_mode_check CHECK ((comparator_mode = ANY (ARRAY['strict'::text, 'normalized'::text, 'semantic'::text, 'none'::text]))),
    CONSTRAINT solve_code_generations_verdict_check CHECK ((verdict = ANY (ARRAY['ok'::text, 'compile_error'::text, 'runtime_error'::text, 'wrong_output'::text, 'extraneous_output'::text])))
);


--
-- Name: solve_eval_run_metrics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solve_eval_run_metrics (
    run_id uuid NOT NULL,
    micro_pass_rate double precision NOT NULL,
    macro_pass_rate double precision NOT NULL,
    subset_accuracy double precision NOT NULL,
    compilability_rate double precision NOT NULL,
    extraneous_output_rate double precision,
    ok_cnt integer NOT NULL,
    comp_err_cnt integer NOT NULL,
    run_err_cnt integer NOT NULL,
    wrong_cnt integer NOT NULL,
    extra_cnt integer NOT NULL,
    total integer NOT NULL,
    details jsonb DEFAULT '{}'::jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: strategies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.strategies (
    name character varying NOT NULL,
    description text
);


--
-- Name: task_change_analyses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_change_analyses (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    task_id uuid NOT NULL,
    task_original_id uuid NOT NULL,
    analyzed_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    text_levenshtein_ratio double precision,
    text_jaccard_similarity double precision,
    text_words_added integer,
    text_words_removed integer,
    text_words_modified integer,
    text_length_delta integer,
    code_levenshtein_ratio double precision,
    code_lines_added integer,
    code_lines_removed integer,
    code_lines_modified integer,
    code_lines_reordered integer,
    code_loc_delta integer,
    code_complexity_delta integer,
    code_token_change_ratio double precision,
    ast_nodes_added integer,
    ast_nodes_removed integer,
    ast_nodes_modified integer,
    ast_structural_score double precision,
    ast_change_types jsonb DEFAULT '[]'::jsonb,
    ast_distractor_changes jsonb DEFAULT '{}'::jsonb,
    overall_change_score double precision,
    change_category character varying(20),
    teacher_rating integer
);


--
-- Name: task_originals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_originals (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    task_id uuid NOT NULL,
    original_problem_statement text,
    original_code text,
    adjustment_rating integer,
    saved_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT task_originals_adjustment_rating_check CHECK (((adjustment_rating >= 1) AND (adjustment_rating <= 5)))
);


--
-- Name: tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tasks (
    id uuid NOT NULL,
    statement text NOT NULL,
    solution text NOT NULL,
    tier_level integer NOT NULL,
    cyclomatic_complexity integer NOT NULL,
    sequence_id uuid NOT NULL,
    distinct_identifier_count integer,
    maximal_nesting_depth integer,
    method_call_count integer,
    lines_of_code integer,
    function_signature text,
    purpose_behavior text,
    pre_post_conditions text,
    distractors text,
    distractor_plan jsonb,
    distractors_enabled boolean DEFAULT true NOT NULL,
    context_before text,
    context_after text,
    search_vector tsvector GENERATED ALWAYS AS ((setweight(to_tsvector('simple'::regconfig, COALESCE(statement, ''::text)), 'A'::"char") || setweight(to_tsvector('simple'::regconfig, COALESCE(solution, ''::text)), 'B'::"char"))) STORED
);


--
-- Name: tiered_task_sequence_concepts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tiered_task_sequence_concepts (
    sequence_id uuid NOT NULL,
    concept_id uuid NOT NULL
);


--
-- Name: tiered_task_sequences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tiered_task_sequences (
    id uuid NOT NULL,
    programming_language character varying NOT NULL,
    code_snippet text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    task_description text,
    version_id uuid NOT NULL,
    strategyname text,
    source_eval_input_id uuid,
    input_mode text,
    owner_id uuid,
    target_language character varying(5) DEFAULT 'de'::character varying,
    constraint_config jsonb,
    tier_count integer DEFAULT 3,
    curriculum_topic_id uuid,
    semantic_summary character varying(500) DEFAULT NULL::character varying,
    search_vector tsvector GENERATED ALWAYS AS ((setweight(to_tsvector('simple'::regconfig, COALESCE(task_description, ''::text)), 'A'::"char") || setweight(to_tsvector('simple'::regconfig, (COALESCE(semantic_summary, ''::character varying))::text), 'B'::"char"))) STORED,
    CONSTRAINT tiered_task_sequences_input_mode_check CHECK (((input_mode IS NULL) OR (input_mode = ANY (ARRAY['full'::text, 'code'::text, 'meta'::text]))))
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    user_id uuid DEFAULT gen_random_uuid() NOT NULL,
    email character varying(320),
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    password_hash character varying(255),
    role public.user_role DEFAULT 'STUDENT'::public.user_role NOT NULL,
    is_verified boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


CREATE OR REPLACE VIEW public.vw_task_distractor_weights AS
SELECT 
    id AS task_id,
    COALESCE(
      CASE (distractor_plan->>'type')
          WHEN 'LOGIC_FLIP' THEN 3.0
          WHEN 'OFF_BY_ONE' THEN 2.5
          WHEN 'VARIABLE_MISMATCH' THEN 2.0
          WHEN 'BOUNDARY_IDENTITY' THEN 2.0
          WHEN 'STRUCTURAL_ILLOGICAL' THEN 1.5
          WHEN 'REDUNDANT_ZOMBIE' THEN 1.0
          ELSE 0.0 
      END, 0.0) AS raw_dz
FROM public.tasks;

--
-- Name: v_ps_stats; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_ps_stats AS
 SELECT (avg(clarity))::double precision AS mu_clarity,
    (stddev_samp(clarity))::double precision AS sd_clarity,
    (avg(curriculum_fit))::double precision AS mu_curric,
    (stddev_samp(curriculum_fit))::double precision AS sd_curric,
    (avg(appropriateness))::double precision AS mu_appro,
    (stddev_samp(appropriateness))::double precision AS sd_appro,
    (avg(motivation))::double precision AS mu_motiv,
    (stddev_samp(motivation))::double precision AS sd_motiv,
    (avg((concept_align)::integer))::double precision AS mu_calign,
    (stddev_samp((concept_align)::integer))::double precision AS sd_calign,
    (avg((form_length)::integer))::double precision AS mu_form,
    (stddev_samp((form_length)::integer))::double precision AS sd_form
   FROM public.problem_statement_ratings;


--
-- Name: v_ps_rating_z_global; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_ps_rating_z_global AS
 SELECT r.id,
    r.generation_id,
    r.rated_at,
    r.clarity,
    r.concept_align,
    r.curriculum_fit,
    r.appropriateness,
    r.motivation,
    r.form_length,
    r.notes,
    r.total_raw,
    (((r.clarity)::double precision - s.mu_clarity) / NULLIF(s.sd_clarity, (0)::double precision)) AS z_clarity,
    ((((r.concept_align)::integer)::double precision - s.mu_calign) / NULLIF(s.sd_calign, (0)::double precision)) AS z_concept_align,
    (((r.curriculum_fit)::double precision - s.mu_curric) / NULLIF(s.sd_curric, (0)::double precision)) AS z_curriculum_fit,
    (((r.appropriateness)::double precision - s.mu_appro) / NULLIF(s.sd_appro, (0)::double precision)) AS z_appropriateness,
    (((r.motivation)::double precision - s.mu_motiv) / NULLIF(s.sd_motiv, (0)::double precision)) AS z_motivation,
    ((((r.form_length)::integer)::double precision - s.mu_form) / NULLIF(s.sd_form, (0)::double precision)) AS z_form_length,
    (((((((((r.clarity)::double precision - s.mu_clarity) / NULLIF(s.sd_clarity, (0)::double precision)) + ((((r.concept_align)::integer)::double precision - s.mu_calign) / NULLIF(s.sd_calign, (0)::double precision))) + (((r.curriculum_fit)::double precision - s.mu_curric) / NULLIF(s.sd_curric, (0)::double precision))) + (((r.appropriateness)::double precision - s.mu_appro) / NULLIF(s.sd_appro, (0)::double precision))) + (((r.motivation)::double precision - s.mu_motiv) / NULLIF(s.sd_motiv, (0)::double precision))) + ((((r.form_length)::integer)::double precision - s.mu_form) / NULLIF(s.sd_form, (0)::double precision))) / (6.0)::double precision) AS z_total
   FROM (public.problem_statement_ratings r
     CROSS JOIN public.v_ps_stats s);


--
-- Name: v_ps_prompt_scores; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_ps_prompt_scores AS
 SELECT p.label AS prompt_label,
    avg(z.z_total) AS mean_z_total,
    count(*) AS n_ratings
   FROM ((public.v_ps_rating_z_global z
     JOIN public.problem_statement_generations g ON ((g.id = z.generation_id)))
     JOIN public.prompt_templates p ON ((p.id = g.prompt_id)))
  GROUP BY p.label
  ORDER BY (avg(z.z_total)) DESC;


--
-- Name: versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.versions (
    id uuid NOT NULL,
    version_name character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    description text
);


--
-- Name: vw_input_mode_complexity; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.vw_input_mode_complexity AS
 SELECT e.id AS eval_input_id,
    tts.input_mode,
    avg(t.lines_of_code) AS avg_loc,
    avg(t.cyclomatic_complexity) AS avg_cc,
    avg(t.method_call_count) AS avg_mcc,
    avg(t.maximal_nesting_depth) AS avg_mnd,
    avg(t.distinct_identifier_count) AS avg_dic,
    count(t.id) AS n_tasks
   FROM ((public.tiered_task_sequences tts
     JOIN public.tasks t ON ((t.sequence_id = tts.id)))
     JOIN public.eval_input e ON ((e.id = tts.source_eval_input_id)))
  GROUP BY e.id, tts.input_mode;


DROP VIEW IF EXISTS public.vw_metric_stats_global CASCADE;
--
-- Name: vw_task_z; Type: VIEW; Schema: public; Owner: -
--

CREATE OR REPLACE VIEW public.vw_metric_stats_by_language AS
 SELECT 
    s.programming_language, -- Jetzt aus der Tabelle tiered_task_sequences
    avg(t.cyclomatic_complexity) AS mu_cy,
    stddev_samp(t.cyclomatic_complexity) AS sd_cy,
    avg(t.lines_of_code) AS mu_loc,
    stddev_samp(t.lines_of_code) AS sd_loc,
    avg(t.maximal_nesting_depth) AS mu_nd,
    stddev_samp(t.maximal_nesting_depth) AS sd_nd,
    avg(t.method_call_count) AS mu_mc,
    stddev_samp(t.method_call_count) AS sd_mc,
    avg(t.distinct_identifier_count) AS mu_id,
    stddev_samp(t.distinct_identifier_count) AS sd_id,
    avg(w.raw_dz) AS mu_dz,
    stddev_samp(w.raw_dz) AS sd_dz,
    count(*) AS n
   FROM public.tasks t
     JOIN public.tiered_task_sequences s ON t.sequence_id = s.id -- Verknüpfung zur Sprache
     LEFT JOIN public.vw_task_distractor_weights w ON t.id = w.task_id
   GROUP BY s.programming_language;



CREATE OR REPLACE VIEW public.vw_task_z AS
SELECT 
    tasks.id AS task_id,
    tasks.sequence_id,
    tasks.tier_level AS tier,
    s.programming_language AS language,
    ((tasks.cyclomatic_complexity::numeric - g.mu_cy) / NULLIF(g.sd_cy, 0)) AS z_cy,
    ((tasks.lines_of_code::numeric - g.mu_loc) / NULLIF(g.sd_loc, 0)) AS z_loc,
    ((tasks.maximal_nesting_depth::numeric - g.mu_nd) / NULLIF(g.sd_nd, 0)) AS z_nd,
    ((tasks.method_call_count::numeric - g.mu_mc) / NULLIF(g.sd_mc, 0)) AS z_mc,
    ((tasks.distinct_identifier_count::numeric - g.mu_id) / NULLIF(g.sd_id, 0)) AS z_id,
    ((w.raw_dz::numeric - g.mu_dz) / NULLIF(g.sd_dz, 0)) AS dz,
    -- Berechnung cz_code
    (
        (
            COALESCE((tasks.cyclomatic_complexity::numeric - g.mu_cy) / NULLIF(g.sd_cy, 0), 0) +
            COALESCE((tasks.lines_of_code::numeric - g.mu_loc) / NULLIF(g.sd_loc, 0), 0) +
            COALESCE((tasks.maximal_nesting_depth::numeric - g.mu_nd) / NULLIF(g.sd_nd, 0), 0) +
            COALESCE((tasks.method_call_count::numeric - g.mu_mc) / NULLIF(g.sd_mc, 0), 0) +
            COALESCE((tasks.distinct_identifier_count::numeric - g.mu_id) / NULLIF(g.sd_id, 0), 0)
        ) / 
        NULLIF(
            (tasks.cyclomatic_complexity IS NOT NULL)::integer +
            (tasks.lines_of_code IS NOT NULL)::integer +
            (tasks.maximal_nesting_depth IS NOT NULL)::integer +
            (tasks.method_call_count IS NOT NULL)::integer +
            (tasks.distinct_identifier_count IS NOT NULL)::integer, 
            0
        )::numeric
    ) AS cz_code,
    -- Berechnung cz_total (Durchschnitt aus cz_code und dz)
    (
        (
            (
                COALESCE((tasks.cyclomatic_complexity::numeric - g.mu_cy) / NULLIF(g.sd_cy, 0), 0) +
                COALESCE((tasks.lines_of_code::numeric - g.mu_loc) / NULLIF(g.sd_loc, 0), 0) +
                COALESCE((tasks.maximal_nesting_depth::numeric - g.mu_nd) / NULLIF(g.sd_nd, 0), 0) +
                COALESCE((tasks.method_call_count::numeric - g.mu_mc) / NULLIF(g.sd_mc, 0), 0) +
                COALESCE((tasks.distinct_identifier_count::numeric - g.mu_id) / NULLIF(g.sd_id, 0), 0)
            ) / 
            NULLIF(
                (tasks.cyclomatic_complexity IS NOT NULL)::integer +
                (tasks.lines_of_code IS NOT NULL)::integer +
                (tasks.maximal_nesting_depth IS NOT NULL)::integer +
                (tasks.method_call_count IS NOT NULL)::integer +
                (tasks.distinct_identifier_count IS NOT NULL)::integer, 
                0
            )::numeric
            + 
            ((w.raw_dz::numeric - g.mu_dz) / NULLIF(g.sd_dz, 0))
        ) / 2.0
    ) AS cz_total
FROM public.tasks
JOIN public.tiered_task_sequences s ON tasks.sequence_id = s.id
LEFT JOIN public.vw_task_distractor_weights w ON tasks.id = w.task_id
JOIN public.vw_metric_stats_by_language g ON s.programming_language = g.programming_language;

--
-- Name: vw_sequence_tier_cz; Type: VIEW; Schema: public; Owner: -
--



--
-- Name: vw_sequence_complexity; Type: VIEW; Schema: public; Owner: -
--
CREATE OR REPLACE VIEW public.vw_sequence_tier_cz AS
SELECT sequence_id, tier, AVG(cz_total) as cz
FROM public.vw_task_z
GROUP BY sequence_id, tier;

CREATE VIEW public.vw_sequence_complexity AS
 WITH tiers AS (
         SELECT vw_sequence_tier_cz.sequence_id,
            max(vw_sequence_tier_cz.cz) FILTER (WHERE (vw_sequence_tier_cz.tier = 1)) AS cz_t1,
            max(vw_sequence_tier_cz.cz) FILTER (WHERE (vw_sequence_tier_cz.tier = 2)) AS cz_t2,
            max(vw_sequence_tier_cz.cz) FILTER (WHERE (vw_sequence_tier_cz.tier = 3)) AS cz_t3
           FROM public.vw_sequence_tier_cz
          GROUP BY vw_sequence_tier_cz.sequence_id
        ), slope AS (
         SELECT vw_sequence_tier_cz.sequence_id,
            regr_slope((vw_sequence_tier_cz.cz)::double precision, (vw_sequence_tier_cz.tier)::double precision) AS beta
           FROM public.vw_sequence_tier_cz
          GROUP BY vw_sequence_tier_cz.sequence_id
        )
 SELECT t.sequence_id,
    t.cz_t1,
    t.cz_t2,
    t.cz_t3,
    (t.cz_t2 - t.cz_t1) AS dcz_12,
    (t.cz_t3 - t.cz_t2) AS dcz_23,
    (t.cz_t3 - t.cz_t1) AS dcz_13,
    s.beta,
    ((t.cz_t3 - t.cz_t1) > (0)::numeric) AS passes_basic,
    (((t.cz_t2 - t.cz_t1) >= 0.2) OR ((t.cz_t3 - t.cz_t2) >= 0.2)) AS passes_step,
    (((t.cz_t2 - t.cz_t1) > (0)::numeric) AND ((t.cz_t3 - t.cz_t2) > (0)::numeric)) AS passes_advanced
   FROM (tiers t
     JOIN slope s USING (sequence_id));


--
-- Name: vw_input_mode_cz; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.vw_input_mode_cz AS
 SELECT e.id AS eval_input_id,
    tts.input_mode,
    avg(s.cz_t1) AS cz_t1,
    avg(s.cz_t2) AS cz_t2,
    avg(s.cz_t3) AS cz_t3,
    avg(s.dcz_12) AS dcz_12,
    avg(s.dcz_23) AS dcz_23,
    avg(s.dcz_13) AS dcz_13,
    avg(s.beta) AS beta,
    bool_or(s.passes_basic) AS passes_basic,
    bool_or(s.passes_step) AS passes_step,
    bool_or(s.passes_advanced) AS passes_advanced
   FROM ((public.vw_sequence_complexity s
     JOIN public.tiered_task_sequences tts ON ((s.sequence_id = tts.id)))
     JOIN public.eval_input e ON ((e.id = tts.source_eval_input_id)))
  GROUP BY e.id, tts.input_mode;


--
-- Name: vw_input_mode_comparison; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.vw_input_mode_comparison AS
 SELECT f.eval_input_id,
    (f.avg_loc - c.avg_loc) AS diff_loc_full_code,
    (f.avg_loc - m.avg_loc) AS diff_loc_full_meta,
    (c.avg_loc - m.avg_loc) AS diff_loc_code_meta,
    (f.avg_cc - c.avg_cc) AS diff_cc_full_code,
    (f.avg_cc - m.avg_cc) AS diff_cc_full_meta,
    (c.avg_cc - m.avg_cc) AS diff_cc_code_meta,
    (f.avg_mnd - c.avg_mnd) AS diff_mnd_full_code,
    (f.avg_mnd - m.avg_mnd) AS diff_mnd_full_meta,
    (c.avg_mnd - m.avg_mnd) AS diff_mnd_code_meta,
    (fc.dcz_13 - cc.dcz_13) AS diff_cz_full_code,
    (fc.dcz_13 - mc.dcz_13) AS diff_cz_full_meta,
    (cc.dcz_13 - mc.dcz_13) AS diff_cz_code_meta,
    (fc.beta - cc.beta) AS diff_beta_full_code,
    (fc.beta - mc.beta) AS diff_beta_full_meta,
    (cc.beta - mc.beta) AS diff_beta_code_meta
   FROM (((((public.vw_input_mode_complexity f
     JOIN public.vw_input_mode_complexity c ON ((f.eval_input_id = c.eval_input_id)))
     JOIN public.vw_input_mode_complexity m ON ((f.eval_input_id = m.eval_input_id)))
     LEFT JOIN public.vw_input_mode_cz fc ON (((f.eval_input_id = fc.eval_input_id) AND (fc.input_mode = 'full'::text))))
     LEFT JOIN public.vw_input_mode_cz cc ON (((f.eval_input_id = cc.eval_input_id) AND (cc.input_mode = 'code'::text))))
     LEFT JOIN public.vw_input_mode_cz mc ON (((f.eval_input_id = mc.eval_input_id) AND (mc.input_mode = 'meta'::text))))
  WHERE ((f.input_mode = 'full'::text) AND (c.input_mode = 'code'::text) AND (m.input_mode = 'meta'::text));


--
-- Name: vw_input_mode_z; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.vw_input_mode_z AS
 WITH stats AS (
         SELECT avg(vw_input_mode_complexity.avg_loc) AS mu_loc,
            stddev_samp(vw_input_mode_complexity.avg_loc) AS sd_loc,
            avg(vw_input_mode_complexity.avg_cc) AS mu_cc,
            stddev_samp(vw_input_mode_complexity.avg_cc) AS sd_cc,
            avg(vw_input_mode_complexity.avg_mcc) AS mu_mcc,
            stddev_samp(vw_input_mode_complexity.avg_mcc) AS sd_mcc,
            avg(vw_input_mode_complexity.avg_mnd) AS mu_mnd,
            stddev_samp(vw_input_mode_complexity.avg_mnd) AS sd_mnd,
            avg(vw_input_mode_complexity.avg_dic) AS mu_dic,
            stddev_samp(vw_input_mode_complexity.avg_dic) AS sd_dic
           FROM public.vw_input_mode_complexity
        )
 SELECT imc.eval_input_id,
    imc.input_mode,
    imc.avg_loc,
    imc.avg_cc,
    imc.avg_mcc,
    imc.avg_mnd,
    imc.avg_dic,
    imc.n_tasks,
    ((imc.avg_loc - s.mu_loc) / NULLIF(s.sd_loc, (0)::numeric)) AS z_loc,
    ((imc.avg_cc - s.mu_cc) / NULLIF(s.sd_cc, (0)::numeric)) AS z_cc,
    ((imc.avg_mcc - s.mu_mcc) / NULLIF(s.sd_mcc, (0)::numeric)) AS z_mcc,
    ((imc.avg_mnd - s.mu_mnd) / NULLIF(s.sd_mnd, (0)::numeric)) AS z_mnd,
    ((imc.avg_dic - s.mu_dic) / NULLIF(s.sd_dic, (0)::numeric)) AS z_dic
   FROM (public.vw_input_mode_complexity imc
     CROSS JOIN stats s);


--
-- Name: vw_input_mode_summary; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.vw_input_mode_summary AS
 SELECT e.snippet_name,
    e.language,
    z.eval_input_id,
    z.input_mode,
    z.avg_loc,
    z.avg_cc,
    z.avg_mnd,
    z.avg_mcc,
    z.avg_dic,
    z.z_loc,
    z.z_cc,
    z.z_mnd,
    z.z_mcc,
    z.z_dic,
    cz.cz_t1,
    cz.cz_t2,
    cz.cz_t3,
    cz.dcz_13,
    cz.beta,
    cz.passes_basic,
    cz.passes_step,
    cz.passes_advanced
   FROM ((public.vw_input_mode_z z
     LEFT JOIN public.vw_input_mode_cz cz ON (((z.eval_input_id = cz.eval_input_id) AND (z.input_mode = cz.input_mode))))
     JOIN public.eval_input e ON ((e.id = z.eval_input_id)));


--
-- Name: vw_input_mode_global_stats; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.vw_input_mode_global_stats AS
 SELECT z.input_mode,
    count(DISTINCT z.eval_input_id) AS n_inputs,
    avg(z.avg_loc) AS mean_loc,
    stddev_samp(z.avg_loc) AS sd_loc,
    avg(z.avg_cc) AS mean_cc,
    stddev_samp(z.avg_cc) AS sd_cc,
    avg(z.avg_mnd) AS mean_mnd,
    stddev_samp(z.avg_mnd) AS sd_mnd,
    avg(z.avg_mcc) AS mean_mcc,
    stddev_samp(z.avg_mcc) AS sd_mcc,
    avg(z.avg_dic) AS mean_dic,
    stddev_samp(z.avg_dic) AS sd_dic,
    avg(cz.dcz_13) AS mean_dcz_13,
    stddev_samp(cz.dcz_13) AS sd_dcz_13,
    avg(cz.beta) AS mean_beta,
    stddev_samp(cz.beta) AS sd_beta,
    avg((cz.passes_basic)::integer) AS share_pass_basic,
    avg((cz.passes_step)::integer) AS share_pass_step,
    avg((cz.passes_advanced)::integer) AS share_pass_advanced
   FROM (public.vw_input_mode_summary z
     LEFT JOIN public.vw_input_mode_cz cz ON (((z.eval_input_id = cz.eval_input_id) AND (z.input_mode = cz.input_mode))))
  GROUP BY z.input_mode
  ORDER BY z.input_mode;


--
-- Name: worksheets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.worksheets (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(200) NOT NULL,
    description text DEFAULT ''::text,
    owner_id uuid,
    hints_enabled boolean DEFAULT true
);


--
-- Name: worksheets_sequence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.worksheets_sequence (
    worksheet_id uuid NOT NULL,
    sequence_id uuid NOT NULL,
    last_used_at timestamp without time zone DEFAULT now()
);






--
-- Data for Name: concept_curriculum_topic; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.concept_curriculum_topic (concept_id, topic_id) FROM stdin;
28b715ba-0c9b-42e3-9d06-4fb059c1f98e	b9000000-0000-0000-0000-000000000001
9590e3be-e9c1-4d14-9a47-6b3f0d995e3a	b9000000-0000-0000-0000-000000000001
b6bc3853-4a17-4548-8ecc-b357e37d9862	b9000000-0000-0000-0000-000000000001
0fe57766-ff28-4c5f-a0da-5df83f94e210	b9000000-0000-0000-0000-000000000001
33d2a4c1-ce5a-4603-a4c8-894ce64bb234	b9000000-0000-0000-0000-000000000002
faf9d1c9-a211-4794-89b7-5222c274b024	b9000000-0000-0000-0000-000000000002
0fe57766-ff28-4c5f-a0da-5df83f94e210	b9000000-0000-0000-0000-000000000002
788380ef-0400-44d1-8145-a66ea6a1ba57	b9000000-0000-0000-0000-000000000003
d51b3cb7-c9b5-48e6-8544-c2c13ba432f0	b9000000-0000-0000-0000-000000000003
2249db0d-6717-4b88-940b-1a7fbd16d4a7	b9000000-0000-0000-0000-000000000003
d7aa6abd-e292-4812-8380-ac6eed2577c3	b9000000-0000-0000-0000-000000000004
d8dbbc0c-6f4b-4621-94f7-9dfdf4cf3ba5	b9000000-0000-0000-0000-000000000004
2206c760-e571-484b-a318-edf380606a7e	b9000000-0000-0000-0000-000000000004
3ef04dce-362b-4a87-b636-9a093b1c00ca	b9000000-0000-0000-0000-000000000005
ef9e508a-6190-48a7-8316-6af23dc931e7	b9000000-0000-0000-0000-000000000005
031473af-2453-4a7c-bad0-846e4b9173cc	b9000000-0000-0000-0000-000000000005
2a3d713d-2225-4446-ab02-718463c640b0	b9000000-0000-0000-0000-000000000005
788380ef-0400-44d1-8145-a66ea6a1ba57	b1000000-0000-0000-0000-000000000001
9da6a6ce-affa-4dfc-8557-9909699a28d3	b1000000-0000-0000-0000-000000000001
43db3a15-bac7-4746-b6b2-9a61c932f3cc	b1000000-0000-0000-0000-000000000001
00f94bf6-3fe8-4cd2-a252-dcf335ecc4bb	b1000000-0000-0000-0000-000000000001
84921497-4886-42b3-bbdf-8ccad9396b17	b1000000-0000-0000-0000-000000000002
158e9313-6777-455d-94be-040630f40afa	b1000000-0000-0000-0000-000000000002
f2a6e591-685b-4f86-9d24-5f68e8c8c252	b1000000-0000-0000-0000-000000000002
99f67f71-ebcd-42ef-9183-dd1a1892eebf	b1000000-0000-0000-0000-000000000002
788380ef-0400-44d1-8145-a66ea6a1ba57	b1000000-0000-0000-0000-000000000003
43db3a15-bac7-4746-b6b2-9a61c932f3cc	b1000000-0000-0000-0000-000000000003
00f94bf6-3fe8-4cd2-a252-dcf335ecc4bb	b1000000-0000-0000-0000-000000000003
11662baf-9cc3-4f0e-be33-033ce74d2344	b1000000-0000-0000-0000-000000000003
7b661e4e-02f5-407c-97be-4d0f0a0319bb	b1000000-0000-0000-0000-000000000003
788380ef-0400-44d1-8145-a66ea6a1ba57	b1100000-0000-0000-0000-000000000001
43db3a15-bac7-4746-b6b2-9a61c932f3cc	b1100000-0000-0000-0000-000000000001
00f94bf6-3fe8-4cd2-a252-dcf335ecc4bb	b1100000-0000-0000-0000-000000000001
0e1e457e-276d-49f7-a13b-d771d979e609	b1100000-0000-0000-0000-000000000001
788380ef-0400-44d1-8145-a66ea6a1ba57	b1100000-0000-0000-0000-000000000002
d51b3cb7-c9b5-48e6-8544-c2c13ba432f0	b1100000-0000-0000-0000-000000000002
00f94bf6-3fe8-4cd2-a252-dcf335ecc4bb	b1100000-0000-0000-0000-000000000002
fb49ead9-faaa-485c-863a-cc46547bfd20	b1100000-0000-0000-0000-000000000002
0e1e457e-276d-49f7-a13b-d771d979e609	b1100000-0000-0000-0000-000000000002
33d2a4c1-ce5a-4603-a4c8-894ce64bb234	b1200000-0000-0000-0000-000000000001
d7aa6abd-e292-4812-8380-ac6eed2577c3	b1200000-0000-0000-0000-000000000001
d8dbbc0c-6f4b-4621-94f7-9dfdf4cf3ba5	b1200000-0000-0000-0000-000000000001
2206c760-e571-484b-a318-edf380606a7e	b1200000-0000-0000-0000-000000000001
a7553d73-8f12-4fee-a655-1bc89a03fc21	b1200000-0000-0000-0000-000000000001
900deadf-295a-40f9-8795-2adf8b27178d	b1200000-0000-0000-0000-000000000002
fb49ead9-faaa-485c-863a-cc46547bfd20	b1200000-0000-0000-0000-000000000002
6aec0c70-eec5-410d-b6d0-83ab42208a0f	b1200000-0000-0000-0000-000000000002
a7553d73-8f12-4fee-a655-1bc89a03fc21	b1200000-0000-0000-0000-000000000002
3ef04dce-362b-4a87-b636-9a093b1c00ca	b1200000-0000-0000-0000-000000000002
33d2a4c1-ce5a-4603-a4c8-894ce64bb234	b1200000-0000-0000-0000-000000000003
a7553d73-8f12-4fee-a655-1bc89a03fc21	b1200000-0000-0000-0000-000000000003
3ef04dce-362b-4a87-b636-9a093b1c00ca	b1200000-0000-0000-0000-000000000003
\.


--
-- Data for Name: concepts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.concepts (id, name, description, generated) FROM stdin;
8f1a2c34-7b28-4d92-a3b0-9c1b3e6f1011	Sequence	Execution of statements in a linear, top-to-bottom order.	f
93c45e2a-1db9-4a75-a567-2e94f2b8b212	Condition	Branching logic based on if/else statements or boolean expressions.	f
f54de678-3a10-4cf4-83bb-873a45ddc313	Loop	Repetition of statements using constructs such as for, while, or foreach.	f
ab12cd34-56ef-47ab-90cd-1234567890ab	Variables	Named storage for values that can be assigned, updated, and referenced.	f
1e2f3a4b-5c6d-7e8f-9a0b-1c2d3e4f5a67	Functions	Encapsulated blocks of code that can be defined, called, and return values.	f
9f8e7d6c-5b4a-3210-9abc-def123456789	Parameters	Inputs passed to functions/methods to generalize behavior.	f
cafe1234-babe-5678-dead-beef98765432	Collections	Grouping of multiple values in arrays, lists, or maps for iteration.	f
01234567-89ab-cdef-0123-456789abcdef	Recursion	A function calling itself to solve problems through repeated substructure.	f
822615cf-e7d1-4aca-a1e3-15e82815ebda	Schleifen	\N	t
977fdf99-c0e2-4802-851b-4b3159e73fbf	Rekursion	\N	t
def5a011-6150-4f02-9110-072162f71826	Arrays	\N	t
7889629c-e9c0-49b2-82b3-915635007537	Return values	\N	t
33d2a4c1-ce5a-4603-a4c8-894ce64bb234	if-else	Executes different code blocks depending on a boolean condition.	f
b61379aa-06ca-4df7-9b50-c747413b2e73	switch-case	Selects one of several code blocks to execute based on the value of a variable.	f
788380ef-0400-44d1-8145-a66ea6a1ba57	for-loop	A loop that repeats a block of code a fixed number of times using a counter variable.	f
d51b3cb7-c9b5-48e6-8544-c2c13ba432f0	while-loop	A loop that repeats a block of code as long as a given condition is true.	f
5e0aa631-0d3b-49a4-9522-71dca8fdc741	do-while-loop	A loop that executes its body at least once and then repeats as long as the condition is true.	f
9da6a6ce-affa-4dfc-8557-9909699a28d3	enhanced-for-loop	A loop that iterates over each element of an array or collection without using an index.	f
2249db0d-6717-4b88-940b-1a7fbd16d4a7	break-continue	Statements that alter loop execution: break exits the loop, continue skips to the next iteration.	f
43db3a15-bac7-4746-b6b2-9a61c932f3cc	nested-loops	A loop contained within another loop, often used to process two-dimensional data or generate combinations.	f
d6cd3571-7fd6-4089-b067-0faba1dfb29d	ternary-operator	A compact inline expression that evaluates a condition and returns one of two values.	f
00f94bf6-3fe8-4cd2-a252-dcf335ecc4bb	array	A fixed-size, indexed collection of elements of the same type.	f
898a782d-71e5-4767-a4f6-090eb34b84d5	arraylist	A dynamic, resizable list backed by an array, part of Java's Collections framework.	f
29dfaf88-f305-49db-b211-88c3cf5d6d45	list	An ordered, mutable sequence of elements in Python, supporting mixed types.	f
ef2fdd82-91cb-4030-9d36-a93ded17f40c	dictionary	A Python collection of key-value pairs allowing fast lookup by key.	f
e5883e62-ee6f-4e6d-a692-2e68a0c8c326	hashmap	A data structure that maps keys to values for fast lookup, insertion, and deletion.	f
0dc28890-5689-44d0-b01e-7b03d9307ae0	set	A collection that stores unique elements with no guaranteed order.	f
900deadf-295a-40f9-8795-2adf8b27178d	stack	A last-in, first-out (LIFO) data structure supporting push and pop operations.	f
fb49ead9-faaa-485c-863a-cc46547bfd20	queue	A first-in, first-out (FIFO) data structure used to process elements in order.	f
6aec0c70-eec5-410d-b6d0-83ab42208a0f	linked-list	A linear data structure where each element (node) holds a value and a reference to the next node.	f
0e1e457e-276d-49f7-a13b-d771d979e609	2d-array	An array of arrays forming a grid or matrix structure with rows and columns.	f
2e80cdd2-ef43-4117-8679-7be40c6a5f85	tuple	An immutable ordered sequence of elements in Python.	f
d7aa6abd-e292-4812-8380-ac6eed2577c3	function-definition	Declaring a reusable named block of code that performs a specific task.	f
d8dbbc0c-6f4b-4621-94f7-9dfdf4cf3ba5	parameters-arguments	Values passed into a function or method to customize its behavior.	f
2206c760-e571-484b-a318-edf380606a7e	return-value	The value a function sends back to its caller after execution.	f
a7553d73-8f12-4fee-a655-1bc89a03fc21	recursion	A technique where a function calls itself to solve a problem by breaking it into smaller subproblems.	f
2be91561-821f-407c-b467-5177512ded8f	method-overloading	Defining multiple methods with the same name but different parameter lists in the same class.	f
3212ac04-9888-43c2-84fb-ca2d9d31c8c1	lambda-expression	An anonymous function that can be passed as an argument or stored in a variable.	f
0a9737f7-83ca-49a1-94e4-a421854d6ad8	higher-order-function	A function that takes another function as an argument or returns a function as its result.	f
dd1ca4c1-a66b-4dc8-a1d5-af332dfba5f7	variable-scope	The region of code in which a variable is accessible, such as local or global scope.	f
d51098ba-6c27-4f03-a134-c213ca166181	default-parameter	A function parameter that has a preset value used when no argument is supplied.	f
3ef04dce-362b-4a87-b636-9a093b1c00ca	class-definition	A blueprint that defines the structure and behavior of objects.	f
ef9e508a-6190-48a7-8316-6af23dc931e7	object-instantiation	Creating an instance of a class using the new keyword or a constructor call.	f
031473af-2453-4a7c-bad0-846e4b9173cc	constructor	A special method called when an object is created, used to initialize its fields.	f
84921497-4886-42b3-bbdf-8ccad9396b17	inheritance	A mechanism by which a class acquires the properties and methods of another class.	f
158e9313-6777-455d-94be-040630f40afa	polymorphism	The ability of objects of different classes to be treated as instances of a common superclass.	f
5b624c30-e4bc-4e4f-8e44-e74909113132	encapsulation	Bundling data and methods within a class while restricting direct access to internal state.	f
6edf91b3-e04f-4f0e-9f78-5d29a8913021	interface	A contract that defines a set of methods a class must implement, without providing an implementation.	f
f2a6e591-685b-4f86-9d24-5f68e8c8c252	abstract-class	A class that cannot be instantiated and may define abstract methods to be implemented by subclasses.	f
99f67f71-ebcd-42ef-9183-dd1a1892eebf	method-overriding	Redefining an inherited method in a subclass to provide a more specific implementation.	f
840dfb37-53c8-4fe8-9e72-eb5d93836a33	static-member	A class-level field or method shared by all instances, accessed without creating an object.	f
2a3d713d-2225-4446-ab02-718463c640b0	getter-setter	Methods that provide controlled read and write access to private class fields.	f
f567c68a-c1b3-44d0-9cd3-5649f77a5b2a	try-catch	A block structure that separates normal execution code from error-handling code.	f
e20ab37a-fff8-4ad5-ad2a-98f39deb826e	throw	Explicitly signaling an error condition by raising an exception object.	f
79fbebab-049b-4886-97e5-30b80d8ac190	finally	A code block that always executes after a try-catch, regardless of whether an exception occurred.	f
6ee5fc54-8e06-4cd3-84fc-6ff8fe09ec8a	custom-exception	A user-defined exception class that extends a built-in exception to represent domain-specific errors.	f
da1d467a-a457-4b47-812b-0418b613e705	console-output	Printing text or values to the standard output (console/terminal).	f
fb1582f0-7b98-4775-9e38-1ea6cbf7fa70	console-input	Reading user input from the standard input (keyboard/console).	f
5f6ea5d2-9e33-47c0-8548-976f150fe385	file-reading	Opening and reading data from a file stored on disk.	f
0b683cec-a0e0-4d1d-aafd-7ebc8b2bcf1b	file-writing	Creating or modifying a file by writing data to it.	f
af649726-b38f-4022-9743-a9b820620b06	string-concatenation	Combining two or more strings into a single string using the + operator or similar methods.	f
6a44f12e-eff2-4401-b7d5-a84c5ccb017a	string-methods	Built-in functions for manipulating strings, such as length, substring, replace, and toUpperCase.	f
12265092-ce99-432f-a2dd-c5a530b76063	string-comparison	Comparing two strings for equality or ordering, using methods like equals() or compareTo().	f
e30ace38-5d78-4b98-9030-41407698219a	string-parsing	Converting a string into another data type, such as an integer or float.	f
b14ede52-1615-46e7-a783-a6546f842ed3	string-splitting	Dividing a string into parts using a delimiter character or pattern.	f
cc1409a7-c348-4c99-b927-d817cec154c0	regular-expressions	Pattern descriptions used to search, match, or replace text within strings.	f
28b715ba-0c9b-42e3-9d06-4fb059c1f98e	variable-declaration	Defining a named storage location with a specific data type and optionally an initial value.	f
9590e3be-e9c1-4d14-9a47-6b3f0d995e3a	data-types	The classification of data values, such as int, float, boolean, char, or String.	f
620c5353-de6f-4fe0-9062-6c935fbaf790	type-casting	Converting a value from one data type to another, either explicitly (cast) or implicitly (coercion).	f
4bc30856-0180-4283-9872-bf4b518875e2	constants	Named values that are assigned once and cannot be changed during program execution.	f
faf9d1c9-a211-4794-89b7-5222c274b024	boolean-logic	Expressions using logical operators (AND, OR, NOT) to combine or negate boolean conditions.	f
b6bc3853-4a17-4548-8ecc-b357e37d9862	arithmetic-operators	Operators for performing mathematical calculations: addition, subtraction, multiplication, division, and modulo.	f
0fe57766-ff28-4c5f-a0da-5df83f94e210	comparison-operators	Operators that compare two values and return a boolean result: ==, !=, <, >, <=, >=.	f
11662baf-9cc3-4f0e-be33-033ce74d2344	sorting	Arranging elements of a collection in a defined order, such as ascending or descending.	f
7b661e4e-02f5-407c-97be-4d0f0a0319bb	linear-search	Sequentially examining elements of a collection one by one to find a target value.	f
d4edcb4d-729a-481d-8c91-23d4b6cd30c8	binary-search	An efficient search algorithm that repeatedly divides a sorted collection in half to locate a value.	f
7a5da4ed-8abe-4c10-9687-18f9f54a4d03	list-comprehension	A concise Python syntax for creating lists by applying an expression to each element of an iterable, optionally with filtering.	f
dc8e609f-5acc-4d1e-a768-4fc72657eaac	iterator	An object that enables sequential traversal of a collection without exposing its internal structure.	f
c80c7f40-e766-4fea-b04a-d50915ae1376	generics	A mechanism for writing type-safe, reusable code that works with different data types specified at compile time.	f
e9cee10d-eb1b-4b8c-8825-b9815ef88320	decorator	A Python design pattern using the @ syntax to modify or extend the behavior of functions or classes.	f
cc3131d8-e7c1-4dd0-a4d8-6cc83dc03193	async-await	A syntax for writing asynchronous, non-blocking code that reads like synchronous code.	f
e2c459e9-9a99-4338-a1ac-d5823b54f8cc	stream-api	A Java API for processing collections of data in a declarative, pipeline-oriented functional style.	f
\.


--
-- Data for Name: curriculum_code_examples; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.curriculum_code_examples (id, topic_id, language, title, code, problem_statement, difficulty, notes, created_at) FROM stdin;
\.


--
-- Data for Name: curriculum_topics; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.curriculum_topics (id, name, description, grade_level, track, learning_area, parent_id, sort_order) FROM stdin;
a0000000-0000-0000-0000-000000000009	Jgst. 9 ÔÇö NTG	Einstieg in die Programmierung: OOP-Grundlagen, Kontrollstrukturen, einfache Datentypen	9	NTG	\N	\N	9
a0000000-0000-0000-0000-000000000010	Jgst. 10 ÔÇö NTG	Datenstrukturen, fortgeschrittene OOP, Sortier-/Suchalgorithmen	10	NTG	\N	\N	10
a0000000-0000-0000-0000-000000000011	Jgst. 11 ÔÇö NTG	Graphen, Codierung/Verschluesselung, Kuenstliche Intelligenz	11	NTG	\N	\N	11
7a3c9e2f-8b4d-4a1c-9f6e-3d5b7c1e8f02	Jgst. 11 ÔÇö Spaet beginnend	Einstieg in Algorithmen und Programmierung (spaet beginnende Informatik)	11	Spaet beginnend	\N	\N	1011
c8e4a1d7-3f9b-4c2e-a5d8-6b0f9e3c2a17	Jgst. 12 ÔÇö Grundlegend	Rekursion, dynamische Datenstrukturen, Binaerbaeume	12	Grundlegend	\N	\N	12
9d2f5b8e-6c1a-4d7b-8e3f-1a9c5d7b2f4e	Jgst. 12 ÔÇö Erhoeht	Zusaetzlich: Boolesche Algebra, formale Sprachen (Einstieg), Rechnerarchitektur	12	Erhoeht	\N	\N	1012
5a8d3c7e-9b2f-4e6a-b1c4-0d7f8a3b9e2c	Jgst. 13 ÔÇö Grundlegend	Formale Sprachen, Automaten, Berechenbarkeit, KI vertieft	13	Grundlegend	\N	\N	13
f1b7c4e9-2a8d-4f5c-a3e6-b8d0f4a2c7e1	Jgst. 13 ÔÇö Erhoeht	Zusaetzlich: NEA/DEA, Kellerautomaten, Turingmaschine, Prolog	13	Erhoeht	\N	\N	1013
b9000000-0000-0000-0000-000000000001	Variablen und Datentypen	Deklaration, Zuweisung, primitive Datentypen (int, double, boolean, String)	9	NTG	Inf9 LB3: Softwareentwicklung	a0000000-0000-0000-0000-000000000009	1
b9000000-0000-0000-0000-000000000002	Kontrollstrukturen: Selektion	if/else-Verzweigungen, geschachtelte Bedingungen	9	NTG	Inf9 LB3: Softwareentwicklung	a0000000-0000-0000-0000-000000000009	2
b9000000-0000-0000-0000-000000000003	Kontrollstrukturen: Schleifen	for-Schleife, while-Schleife, Zaehler- und bedingungsgesteuerte Wiederholung	9	NTG	Inf9 LB3: Softwareentwicklung	a0000000-0000-0000-0000-000000000009	3
b9000000-0000-0000-0000-000000000004	Methoden und Parameter	Methodendefinition, Aufruf, Parameter, Rueckgabewerte	9	NTG	Inf9 LB3: Softwareentwicklung	a0000000-0000-0000-0000-000000000009	4
b9000000-0000-0000-0000-000000000005	Klassen und Objekte	Klassendiagramm, Attribute, Konstruktoren, Objekte erzeugen	9	NTG	Inf9 LB3: Softwareentwicklung	a0000000-0000-0000-0000-000000000009	5
b1000000-0000-0000-0000-000000000001	Arrays und Listen	Eindimensionale Arrays, Indexzugriff, Iteration, einfache Listenoperationen	10	NTG	Inf10 LB2: Datenstrukturen und OOP	a0000000-0000-0000-0000-000000000010	1
b1000000-0000-0000-0000-000000000002	Vererbung und Polymorphismus	Generalisierung/Spezialisierung, Methodenueberschreibung, Polymorphismus (ansatzweise)	10	NTG	Inf10 LB2: Datenstrukturen und OOP	a0000000-0000-0000-0000-000000000010	2
b1000000-0000-0000-0000-000000000003	Such- und Sortieralgorithmen	Lineare Suche, Selection Sort, Insertion Sort	10	NTG	Inf10 LB2: Datenstrukturen und OOP	a0000000-0000-0000-0000-000000000010	3
b1100000-0000-0000-0000-000000000001	Graphen und Adjazenzmatrix	Graphendarstellung, Adjazenzmatrix, Knotengrad, Nachbarn	11	NTG	Inf11 LB1: Graphen	a0000000-0000-0000-0000-000000000011	1
b1100000-0000-0000-0000-000000000002	Graphalgorithmen: BFS und Dijkstra	Breitensuche, kuerzeste Wege, Dijkstra-Algorithmus	11	NTG	Inf11 LB1: Graphen	a0000000-0000-0000-0000-000000000011	2
b1100000-0000-0000-0000-000000000003	Codierung und Verschluesselung	Binaer-/Hexadezimalsystem, Caesar-Chiffre, Public-Key-Prinzip	11	NTG	Inf11 LB2: Codierung	a0000000-0000-0000-0000-000000000011	3
b1200000-0000-0000-0000-000000000001	Rekursion	Lineare und verzweigte Rekursion: Fakultaet, Fibonacci, ggT, Tuerme von Hanoi	12	Grundlegend	Inf12 LB1: Rekursion	c8e4a1d7-3f9b-4c2e-a5d8-6b0f9e3c2a17	1
b1200000-0000-0000-0000-000000000002	Verkettete Listen	Einfach verkettete Liste, Kompositum-Pattern, Stack und Queue	12	Grundlegend	Inf12 LB2: Dynamische Datenstrukturen	c8e4a1d7-3f9b-4c2e-a5d8-6b0f9e3c2a17	2
b1200000-0000-0000-0000-000000000003	Binaerbaeume	Binaerer Suchbaum, Traversierungen (Inorder/Preorder/Postorder)	12	Grundlegend	Inf12 LB2: Dynamische Datenstrukturen	c8e4a1d7-3f9b-4c2e-a5d8-6b0f9e3c2a17	3
\.

















--
-- Data for Name: strategies; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.strategies (name, description) FROM stdin;
CodeOnlyStrategy	Given a code snippet: infer the task description from code, extract concepts from code, detect language if missing, then generate a tiered task sequence using (inferred task, extracted concepts, the same code).
ConceptOnlyStrategy	Given a concept list: draft a suitable task description for those concepts, synthesize an example solution (code) for that task, then generate a tiered task sequence using (provided concepts, synthesized code).
TaskAndCodeStrategy	Given a task description and a code snippet: detect programming language from the code, extract concepts from the code, then generate a tiered task sequence using (task, extracted concepts, code).
TaskAndConceptStrategy	Given a task description and a concept list: synthesize a solution (code) for the task???respecting the language if specified???then generate a tiered task sequence using (task, provided concepts, synthesized code).
CodeAndConceptStrategy	Given a code snippet and a concept list (and task text if provided): detect language from the code and generate a tiered task sequence using (task text when present, provided concepts, code).
TaskOnlyStrategy	Given only a task description: synthesize a solution (code) for the task, extract concepts from that code, then generate a tiered task sequence using (task, inferred concepts, synthesized code).
TaskConceptAndCodeStrategy	Given task description, concept list, and code snippet: ensure/detect language and generate a tiered task sequence using all three inputs.
ManualCreation	Teacher-created task sequence without LLM involvement. Code and problem statements are provided directly.
\.








--
-- Data for Name: versions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.versions (id, version_name, created_at, description) FROM stdin;
aa09775e-f584-49cd-a0f2-196464f88fa9	prototype-phase	2025-07-21 09:16:02.723205	Tasks in this version are generated in early-stage development and may lack quality or stability.
f8bec363-6565-46dc-b061-62387d5b9082	PromptEngineeringStep1	2025-09-01 15:47:10.523508	Just checking what the benchmark is before optimization.
c873574a-af80-44cc-8b9d-9bc742762059	RQ1-Eval	2025-10-09 08:46:36.191412	Running the Evaluation on RQ1
\.




--
-- Name: auth_tokens auth_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_tokens
    ADD CONSTRAINT auth_tokens_pkey PRIMARY KEY (token_id);


--
-- Name: auth_tokens auth_tokens_token_value_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_tokens
    ADD CONSTRAINT auth_tokens_token_value_key UNIQUE (token_value);


--
-- Name: class_students class_students_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_students
    ADD CONSTRAINT class_students_pkey PRIMARY KEY (class_id, student_id);


--
-- Name: class_worksheets class_worksheets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_worksheets
    ADD CONSTRAINT class_worksheets_pkey PRIMARY KEY (class_id, worksheet_id);


--
-- Name: classes classes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classes
    ADD CONSTRAINT classes_pkey PRIMARY KEY (id);


--
-- Name: concept_curriculum_topic concept_curriculum_topic_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.concept_curriculum_topic
    ADD CONSTRAINT concept_curriculum_topic_pkey PRIMARY KEY (concept_id, topic_id);


--
-- Name: concepts concepts_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.concepts
    ADD CONSTRAINT concepts_name_key UNIQUE (name);


--
-- Name: concepts concepts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.concepts
    ADD CONSTRAINT concepts_pkey PRIMARY KEY (id);


--
-- Name: curriculum_code_examples curriculum_code_examples_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_code_examples
    ADD CONSTRAINT curriculum_code_examples_pkey PRIMARY KEY (id);


--
-- Name: curriculum_topics curriculum_topics_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_topics
    ADD CONSTRAINT curriculum_topics_name_key UNIQUE (name);


--
-- Name: curriculum_topics curriculum_topics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_topics
    ADD CONSTRAINT curriculum_topics_pkey PRIMARY KEY (id);


--
-- Name: eval_input eval_input_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.eval_input
    ADD CONSTRAINT eval_input_pkey PRIMARY KEY (id);


--
-- Name: eval_result eval_result_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.eval_result
    ADD CONSTRAINT eval_result_pkey PRIMARY KEY (run_id, snippet_name);


--
-- Name: eval_run eval_run_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.eval_run
    ADD CONSTRAINT eval_run_pkey PRIMARY KEY (id);


--
-- Name: feedback_logs feedback_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_logs
    ADD CONSTRAINT feedback_logs_pkey PRIMARY KEY (id);


--
-- Name: hint_usage hint_usage_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hint_usage
    ADD CONSTRAINT hint_usage_pkey PRIMARY KEY (id);


--
-- Name: learning_events learning_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.learning_events
    ADD CONSTRAINT learning_events_pkey PRIMARY KEY (id);


--
-- Name: strategies primary_key_strategy; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.strategies
    ADD CONSTRAINT primary_key_strategy PRIMARY KEY (name);


--
-- Name: problem_statement_generations problem_statement_generations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.problem_statement_generations
    ADD CONSTRAINT problem_statement_generations_pkey PRIMARY KEY (id);


--
-- Name: problem_statement_ratings problem_statement_ratings_generation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.problem_statement_ratings
    ADD CONSTRAINT problem_statement_ratings_generation_id_key UNIQUE (generation_id);


--
-- Name: problem_statement_ratings problem_statement_ratings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.problem_statement_ratings
    ADD CONSTRAINT problem_statement_ratings_pkey PRIMARY KEY (id);


--
-- Name: prompt_templates prompt_templates_label_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prompt_templates
    ADD CONSTRAINT prompt_templates_label_key UNIQUE (label);


--
-- Name: prompt_templates prompt_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prompt_templates
    ADD CONSTRAINT prompt_templates_pkey PRIMARY KEY (id);


--
-- Name: sequence_tags sequence_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sequence_tags
    ADD CONSTRAINT sequence_tags_pkey PRIMARY KEY (sequence_id, tag_name);


--
-- Name: snippet_concept snippet_concept_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.snippet_concept
    ADD CONSTRAINT snippet_concept_pkey PRIMARY KEY (snippet_name, concept_id);


--
-- Name: snippet_sources snippet_sources_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.snippet_sources
    ADD CONSTRAINT snippet_sources_pkey PRIMARY KEY (snippet_name, repo_url, repo_commit);


--
-- Name: snippets snippets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.snippets
    ADD CONSTRAINT snippets_pkey PRIMARY KEY (name);


--
-- Name: solve_code_generations solve_code_generations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solve_code_generations
    ADD CONSTRAINT solve_code_generations_pkey PRIMARY KEY (id);


--
-- Name: solve_code_generations solve_code_generations_run_id_snippet_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solve_code_generations
    ADD CONSTRAINT solve_code_generations_run_id_snippet_name_key UNIQUE (run_id, snippet_name);


--
-- Name: solve_eval_run_metrics solve_eval_run_metrics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solve_eval_run_metrics
    ADD CONSTRAINT solve_eval_run_metrics_pkey PRIMARY KEY (run_id);


--
-- Name: strategies strategies_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.strategies
    ADD CONSTRAINT strategies_name_key UNIQUE (name);


--
-- Name: task_change_analyses task_change_analyses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_change_analyses
    ADD CONSTRAINT task_change_analyses_pkey PRIMARY KEY (id);


--
-- Name: task_change_analyses task_change_analyses_task_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_change_analyses
    ADD CONSTRAINT task_change_analyses_task_id_key UNIQUE (task_id);


--
-- Name: task_originals task_originals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_originals
    ADD CONSTRAINT task_originals_pkey PRIMARY KEY (id);


--
-- Name: task_originals task_originals_task_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_originals
    ADD CONSTRAINT task_originals_task_id_key UNIQUE (task_id);


--
-- Name: tasks tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (id);


--
-- Name: tiered_task_sequence_concepts tiered_task_sequence_concepts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tiered_task_sequence_concepts
    ADD CONSTRAINT tiered_task_sequence_concepts_pkey PRIMARY KEY (sequence_id, concept_id);


--
-- Name: tiered_task_sequences tiered_task_sequences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tiered_task_sequences
    ADD CONSTRAINT tiered_task_sequences_pkey PRIMARY KEY (id);


--
-- Name: concepts unique_concept_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.concepts
    ADD CONSTRAINT unique_concept_name UNIQUE (name);


--
-- Name: versions unique_version_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.versions
    ADD CONSTRAINT unique_version_name UNIQUE (version_name);


--
-- Name: tiered_task_sequences uq_seq_run_input_mode; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tiered_task_sequences
    ADD CONSTRAINT uq_seq_run_input_mode UNIQUE (version_id, source_eval_input_id, input_mode);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);


--
-- Name: versions versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.versions
    ADD CONSTRAINT versions_pkey PRIMARY KEY (id);


--
-- Name: worksheets worksheets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.worksheets
    ADD CONSTRAINT worksheets_pkey PRIMARY KEY (id);


--
-- Name: worksheets_sequence worksheets_sequence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.worksheets_sequence
    ADD CONSTRAINT worksheets_sequence_pkey PRIMARY KEY (worksheet_id, sequence_id);


--
-- Name: idx_auth_tokens_token_value; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_tokens_token_value ON public.auth_tokens USING btree (token_value);


--
-- Name: idx_auth_tokens_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_auth_tokens_user_id ON public.auth_tokens USING btree (user_id);


--
-- Name: idx_change_analyses_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_change_analyses_category ON public.task_change_analyses USING btree (change_category);


--
-- Name: idx_change_analyses_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_change_analyses_task ON public.task_change_analyses USING btree (task_id);


--
-- Name: idx_class_students_student_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_class_students_student_id ON public.class_students USING btree (student_id);


--
-- Name: idx_class_worksheets_worksheet_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_class_worksheets_worksheet_id ON public.class_worksheets USING btree (worksheet_id);


--
-- Name: idx_classes_owner_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_classes_owner_id ON public.classes USING btree (owner_id);


--
-- Name: idx_concept_curriculum_topic_concept; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_concept_curriculum_topic_concept ON public.concept_curriculum_topic USING btree (concept_id);


--
-- Name: idx_concept_curriculum_topic_topic; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_concept_curriculum_topic_topic ON public.concept_curriculum_topic USING btree (topic_id);


--
-- Name: idx_curriculum_examples_lang; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_examples_lang ON public.curriculum_code_examples USING btree (language);


--
-- Name: idx_curriculum_examples_topic; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_examples_topic ON public.curriculum_code_examples USING btree (topic_id);


--
-- Name: idx_curriculum_topics_grade; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_topics_grade ON public.curriculum_topics USING btree (grade_level);


--
-- Name: idx_curriculum_topics_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_curriculum_topics_parent ON public.curriculum_topics USING btree (parent_id);


--
-- Name: idx_eval_input_lang; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_eval_input_lang ON public.eval_input USING btree (language);


--
-- Name: idx_feedback_logs_class_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_feedback_logs_class_id ON public.feedback_logs USING btree (class_id);


--
-- Name: idx_feedback_logs_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_feedback_logs_user_id ON public.feedback_logs USING btree (user_id);


--
-- Name: idx_hint_usage_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hint_usage_task ON public.hint_usage USING btree (task_id);


--
-- Name: idx_hint_usage_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_hint_usage_user ON public.hint_usage USING btree (user_id);


--
-- Name: idx_learning_events_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_learning_events_created ON public.learning_events USING btree (created_at);


--
-- Name: idx_learning_events_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_learning_events_task ON public.learning_events USING btree (task_id);


--
-- Name: idx_learning_events_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_learning_events_type ON public.learning_events USING btree (event_type);


--
-- Name: idx_learning_events_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_learning_events_user ON public.learning_events USING btree (user_id);


--
-- Name: idx_seq_input; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_seq_input ON public.tiered_task_sequences USING btree (source_eval_input_id, input_mode);


--
-- Name: idx_sequence_tags_sequence; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sequence_tags_sequence ON public.sequence_tags USING btree (sequence_id);


--
-- Name: idx_sequence_tags_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sequence_tags_source ON public.sequence_tags USING btree (source);


--
-- Name: idx_sequence_tags_tag; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sequence_tags_tag ON public.sequence_tags USING btree (tag_name);


--
-- Name: idx_sequence_tags_tag_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sequence_tags_tag_trgm ON public.sequence_tags USING gin (tag_name public.gin_trgm_ops);


--
-- Name: idx_sequences_desc_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sequences_desc_trgm ON public.tiered_task_sequences USING gin (task_description public.gin_trgm_ops);


--
-- Name: idx_sequences_fts; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sequences_fts ON public.tiered_task_sequences USING gin (search_vector);


--
-- Name: idx_sequences_owner_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sequences_owner_id ON public.tiered_task_sequences USING btree (owner_id);


--
-- Name: idx_snippet_concept_concept; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_snippet_concept_concept ON public.snippet_concept USING btree (concept_id);


--
-- Name: idx_snippets_language; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_snippets_language ON public.snippets USING btree (language);


--
-- Name: idx_solve_code_generations_run; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solve_code_generations_run ON public.solve_code_generations USING btree (run_id);


--
-- Name: idx_solve_code_generations_snippet; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solve_code_generations_snippet ON public.solve_code_generations USING btree (snippet_name);


--
-- Name: idx_tasks_fts; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tasks_fts ON public.tasks USING gin (search_vector);


--
-- Name: idx_users_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_email ON public.users USING btree (email);


--
-- Name: idx_users_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_role ON public.users USING btree (role);


--
-- Name: idx_worksheets_owner_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_worksheets_owner_id ON public.worksheets USING btree (owner_id);


--
-- Name: ix_psg_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_psg_created_at ON public.problem_statement_generations USING btree (created_at);


--
-- Name: ix_psg_prompt_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_psg_prompt_id ON public.problem_statement_generations USING btree (prompt_id);


--
-- Name: ix_psg_snippet_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_psg_snippet_name ON public.problem_statement_generations USING btree (snippet_name);


--
-- Name: ix_psr_rated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_psr_rated_at ON public.problem_statement_ratings USING btree (rated_at);


--
-- Name: ix_snippet_sources_repo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_snippet_sources_repo ON public.snippet_sources USING btree (repo_url);


--
-- Name: auth_tokens auth_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.auth_tokens
    ADD CONSTRAINT auth_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: class_students class_students_class_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_students
    ADD CONSTRAINT class_students_class_id_fkey FOREIGN KEY (class_id) REFERENCES public.classes(id) ON DELETE CASCADE;


--
-- Name: class_students class_students_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_students
    ADD CONSTRAINT class_students_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: class_worksheets class_worksheets_class_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_worksheets
    ADD CONSTRAINT class_worksheets_class_id_fkey FOREIGN KEY (class_id) REFERENCES public.classes(id) ON DELETE CASCADE;


--
-- Name: class_worksheets class_worksheets_worksheet_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_worksheets
    ADD CONSTRAINT class_worksheets_worksheet_id_fkey FOREIGN KEY (worksheet_id) REFERENCES public.worksheets(id) ON DELETE CASCADE;


--
-- Name: classes classes_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classes
    ADD CONSTRAINT classes_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: concept_curriculum_topic concept_curriculum_topic_concept_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.concept_curriculum_topic
    ADD CONSTRAINT concept_curriculum_topic_concept_id_fkey FOREIGN KEY (concept_id) REFERENCES public.concepts(id) ON DELETE CASCADE;


--
-- Name: concept_curriculum_topic concept_curriculum_topic_topic_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.concept_curriculum_topic
    ADD CONSTRAINT concept_curriculum_topic_topic_id_fkey FOREIGN KEY (topic_id) REFERENCES public.curriculum_topics(id) ON DELETE CASCADE;


--
-- Name: curriculum_code_examples curriculum_code_examples_topic_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_code_examples
    ADD CONSTRAINT curriculum_code_examples_topic_id_fkey FOREIGN KEY (topic_id) REFERENCES public.curriculum_topics(id) ON DELETE CASCADE;


--
-- Name: curriculum_topics curriculum_topics_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_topics
    ADD CONSTRAINT curriculum_topics_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.curriculum_topics(id) ON DELETE SET NULL;


--
-- Name: eval_result eval_result_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.eval_result
    ADD CONSTRAINT eval_result_run_id_fkey FOREIGN KEY (run_id) REFERENCES public.eval_run(id) ON DELETE CASCADE;


--
-- Name: eval_result eval_result_snippet_name_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.eval_result
    ADD CONSTRAINT eval_result_snippet_name_fkey FOREIGN KEY (snippet_name) REFERENCES public.snippets(name) ON DELETE CASCADE;


--
-- Name: tiered_task_sequences fk_sequences_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tiered_task_sequences
    ADD CONSTRAINT fk_sequences_owner FOREIGN KEY (owner_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: tiered_task_sequences fk_strategy_name; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tiered_task_sequences
    ADD CONSTRAINT fk_strategy_name FOREIGN KEY (strategyname) REFERENCES public.strategies(name);


--
-- Name: worksheets fk_worksheets_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.worksheets
    ADD CONSTRAINT fk_worksheets_owner FOREIGN KEY (owner_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: hint_usage hint_usage_task_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hint_usage
    ADD CONSTRAINT hint_usage_task_id_fkey FOREIGN KEY (task_id) REFERENCES public.tasks(id) ON DELETE CASCADE;


--
-- Name: hint_usage hint_usage_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hint_usage
    ADD CONSTRAINT hint_usage_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: learning_events learning_events_sequence_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.learning_events
    ADD CONSTRAINT learning_events_sequence_id_fkey FOREIGN KEY (sequence_id) REFERENCES public.tiered_task_sequences(id) ON DELETE SET NULL;


--
-- Name: learning_events learning_events_task_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.learning_events
    ADD CONSTRAINT learning_events_task_id_fkey FOREIGN KEY (task_id) REFERENCES public.tasks(id) ON DELETE CASCADE;


--
-- Name: learning_events learning_events_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.learning_events
    ADD CONSTRAINT learning_events_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: problem_statement_generations problem_statement_generations_prompt_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.problem_statement_generations
    ADD CONSTRAINT problem_statement_generations_prompt_id_fkey FOREIGN KEY (prompt_id) REFERENCES public.prompt_templates(id) ON DELETE RESTRICT;


--
-- Name: problem_statement_generations problem_statement_generations_snippet_name_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.problem_statement_generations
    ADD CONSTRAINT problem_statement_generations_snippet_name_fkey FOREIGN KEY (snippet_name) REFERENCES public.snippets(name) ON DELETE RESTRICT;


--
-- Name: problem_statement_ratings problem_statement_ratings_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.problem_statement_ratings
    ADD CONSTRAINT problem_statement_ratings_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.problem_statement_generations(id) ON DELETE CASCADE;


--
-- Name: sequence_tags sequence_tags_sequence_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sequence_tags
    ADD CONSTRAINT sequence_tags_sequence_id_fkey FOREIGN KEY (sequence_id) REFERENCES public.tiered_task_sequences(id) ON DELETE CASCADE;


--
-- Name: snippet_concept snippet_concept_concept_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.snippet_concept
    ADD CONSTRAINT snippet_concept_concept_id_fkey FOREIGN KEY (concept_id) REFERENCES public.concepts(id) ON DELETE CASCADE;


--
-- Name: snippet_concept snippet_concept_snippet_name_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.snippet_concept
    ADD CONSTRAINT snippet_concept_snippet_name_fkey FOREIGN KEY (snippet_name) REFERENCES public.snippets(name) ON DELETE CASCADE;


--
-- Name: snippet_sources snippet_sources_snippet_name_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.snippet_sources
    ADD CONSTRAINT snippet_sources_snippet_name_fkey FOREIGN KEY (snippet_name) REFERENCES public.snippets(name) ON DELETE CASCADE;


--
-- Name: solve_code_generations solve_code_generations_ps_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solve_code_generations
    ADD CONSTRAINT solve_code_generations_ps_generation_id_fkey FOREIGN KEY (ps_generation_id) REFERENCES public.problem_statement_generations(id);


--
-- Name: solve_code_generations solve_code_generations_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solve_code_generations
    ADD CONSTRAINT solve_code_generations_run_id_fkey FOREIGN KEY (run_id) REFERENCES public.eval_run(id) ON DELETE CASCADE;


--
-- Name: solve_code_generations solve_code_generations_snippet_name_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solve_code_generations
    ADD CONSTRAINT solve_code_generations_snippet_name_fkey FOREIGN KEY (snippet_name) REFERENCES public.snippets(name);


--
-- Name: solve_eval_run_metrics solve_eval_run_metrics_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solve_eval_run_metrics
    ADD CONSTRAINT solve_eval_run_metrics_run_id_fkey FOREIGN KEY (run_id) REFERENCES public.eval_run(id) ON DELETE CASCADE;


--
-- Name: task_change_analyses task_change_analyses_task_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_change_analyses
    ADD CONSTRAINT task_change_analyses_task_id_fkey FOREIGN KEY (task_id) REFERENCES public.tasks(id) ON DELETE CASCADE;


--
-- Name: task_change_analyses task_change_analyses_task_original_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_change_analyses
    ADD CONSTRAINT task_change_analyses_task_original_id_fkey FOREIGN KEY (task_original_id) REFERENCES public.task_originals(id) ON DELETE CASCADE;


--
-- Name: task_originals task_originals_task_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_originals
    ADD CONSTRAINT task_originals_task_id_fkey FOREIGN KEY (task_id) REFERENCES public.tasks(id) ON DELETE CASCADE;


--
-- Name: tasks tasks_sequence_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT tasks_sequence_id_fkey FOREIGN KEY (sequence_id) REFERENCES public.tiered_task_sequences(id) ON DELETE CASCADE;


--
-- Name: tiered_task_sequence_concepts tiered_task_sequence_concepts_concept_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tiered_task_sequence_concepts
    ADD CONSTRAINT tiered_task_sequence_concepts_concept_id_fkey FOREIGN KEY (concept_id) REFERENCES public.concepts(id) ON DELETE CASCADE;


--
-- Name: tiered_task_sequence_concepts tiered_task_sequence_concepts_sequence_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tiered_task_sequence_concepts
    ADD CONSTRAINT tiered_task_sequence_concepts_sequence_id_fkey FOREIGN KEY (sequence_id) REFERENCES public.tiered_task_sequences(id) ON DELETE CASCADE;


--
-- Name: tiered_task_sequences tiered_task_sequences_curriculum_topic_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tiered_task_sequences
    ADD CONSTRAINT tiered_task_sequences_curriculum_topic_id_fkey FOREIGN KEY (curriculum_topic_id) REFERENCES public.curriculum_topics(id) ON DELETE SET NULL;


--
-- Name: tiered_task_sequences tiered_task_sequences_source_eval_input_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tiered_task_sequences
    ADD CONSTRAINT tiered_task_sequences_source_eval_input_id_fkey FOREIGN KEY (source_eval_input_id) REFERENCES public.eval_input(id) ON DELETE SET NULL;


--
-- Name: tiered_task_sequences tiered_task_sequences_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tiered_task_sequences
    ADD CONSTRAINT tiered_task_sequences_version_id_fkey FOREIGN KEY (version_id) REFERENCES public.versions(id);


--
-- Name: worksheets_sequence worksheets_sequence_sequence_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.worksheets_sequence
    ADD CONSTRAINT worksheets_sequence_sequence_id_fkey FOREIGN KEY (sequence_id) REFERENCES public.tiered_task_sequences(id) ON DELETE CASCADE;


--
-- Name: worksheets_sequence worksheets_sequence_worksheet_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.worksheets_sequence
    ADD CONSTRAINT worksheets_sequence_worksheet_id_fkey FOREIGN KEY (worksheet_id) REFERENCES public.worksheets(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

