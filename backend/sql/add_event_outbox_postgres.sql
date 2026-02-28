CREATE SEQUENCE "public"."event_outbox_id_seq"
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

CREATE TABLE "public"."event_outbox" (
  "id" int8 NOT NULL DEFAULT nextval('event_outbox_id_seq'::regclass),
  "topic" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "message_key" varchar(255) COLLATE "pg_catalog"."default",
  "payload" text COLLATE "pg_catalog"."default" NOT NULL,
  "status" int2 NOT NULL DEFAULT 0,
  "retry_count" int4 NOT NULL DEFAULT 0,
  "next_retry_time" timestamp(6) NOT NULL,
  "created_at" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_at" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "last_error" varchar(1000) COLLATE "pg_catalog"."default"
)
;

CREATE INDEX "idx_event_outbox_status_time" ON "public"."event_outbox" USING btree (
  "status" "pg_catalog"."int2_ops" ASC NULLS LAST,
  "next_retry_time" "pg_catalog"."timestamp_ops" ASC NULLS LAST
);

ALTER TABLE "public"."event_outbox" ADD CONSTRAINT "event_outbox_pkey" PRIMARY KEY ("id");
