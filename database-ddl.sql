-- For use with PostgreSQL

CREATE TABLE public.users (
	id serial NOT NULL,
	email text NOT NULL,
	password_hash text NOT NULL,
	created_at timestamptz NOT NULL DEFAULT now(),
	CONSTRAINT users_email_key UNIQUE (email),
	CONSTRAINT users_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX email_unique ON public.users USING btree (lower(email));

CREATE TABLE public.email_verifications (
	id serial NOT NULL,
	user_id int4 NOT NULL,
	email text NOT NULL,
	verification_token text NOT NULL,
	expires_at timestamptz NOT NULL,
	verified bool NOT NULL DEFAULT false,
	created_at timestamptz NOT NULL DEFAULT now(),
	CONSTRAINT email_verifications_pkey PRIMARY KEY (id),
	CONSTRAINT email_verifications_verify_token_key UNIQUE (verification_token),
	CONSTRAINT expires_at_greater_than_created_at CHECK ((expires_at >= created_at))
);

ALTER TABLE public.email_verifications ADD CONSTRAINT email_verifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

CREATE TABLE public.password_reset_tokens (
	id serial NOT NULL,
	user_id int4 NULL,
	reset_token text NOT NULL,
	expires_at timestamptz NOT NULL,
	used_at timestamptz NULL,
	created_at timestamptz NOT NULL DEFAULT now(),
	CONSTRAINT expires_at_greater_than_created_at CHECK ((expires_at > created_at)),
	CONSTRAINT expires_at_greater_than_used_at CHECK ((expires_at > used_at)),
	CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id),
	CONSTRAINT password_reset_tokens_reset_token_key UNIQUE (reset_token)
);

ALTER TABLE public.password_reset_tokens ADD CONSTRAINT password_reset_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

CREATE TABLE public.sessions (
	id serial NOT NULL,
	user_id int4 NULL,
	session_key text NOT NULL,
	user_agent text NOT NULL,
	ip_address inet NOT NULL,
	expires_at timestamptz NOT NULL,
	created_at timestamptz NOT NULL DEFAULT now(),
	anti_forgery_token text NOT NULL,
	CONSTRAINT expires_at_greater_than_created_at CHECK ((expires_at > created_at)),
	CONSTRAINT sessions_pkey PRIMARY KEY (id),
	CONSTRAINT sessions_session_key_key UNIQUE (session_key)
);

ALTER TABLE public.sessions ADD CONSTRAINT sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
