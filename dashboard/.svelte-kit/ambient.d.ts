
// this file is generated — do not edit it


/// <reference types="@sveltejs/kit" />

/**
 * This module provides access to environment variables that are injected _statically_ into your bundle at build time and are limited to _private_ access.
 * 
 * |         | Runtime                                                                    | Build time                                                               |
 * | ------- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
 * | Private | [`$env/dynamic/private`](https://svelte.dev/docs/kit/$env-dynamic-private) | [`$env/static/private`](https://svelte.dev/docs/kit/$env-static-private) |
 * | Public  | [`$env/dynamic/public`](https://svelte.dev/docs/kit/$env-dynamic-public)   | [`$env/static/public`](https://svelte.dev/docs/kit/$env-static-public)   |
 * 
 * Static environment variables are [loaded by Vite](https://vitejs.dev/guide/env-and-mode.html#env-files) from `.env` files and `process.env` at build time and then statically injected into your bundle at build time, enabling optimisations like dead code elimination.
 * 
 * **_Private_ access:**
 * 
 * - This module cannot be imported into client-side code
 * - This module only includes variables that _do not_ begin with [`config.kit.env.publicPrefix`](https://svelte.dev/docs/kit/configuration#env) _and do_ start with [`config.kit.env.privatePrefix`](https://svelte.dev/docs/kit/configuration#env) (if configured)
 * 
 * For example, given the following build time environment:
 * 
 * ```env
 * ENVIRONMENT=production
 * PUBLIC_BASE_URL=http://site.com
 * ```
 * 
 * With the default `publicPrefix` and `privatePrefix`:
 * 
 * ```ts
 * import { ENVIRONMENT, PUBLIC_BASE_URL } from '$env/static/private';
 * 
 * console.log(ENVIRONMENT); // => "production"
 * console.log(PUBLIC_BASE_URL); // => throws error during build
 * ```
 * 
 * The above values will be the same _even if_ different values for `ENVIRONMENT` or `PUBLIC_BASE_URL` are set at runtime, as they are statically replaced in your code with their build time values.
 */
declare module '$env/static/private' {
	export const INFERENCE_RESULTS_ENABLED: string;
	export const CPLUS_INCLUDE_PATH: string;
	export const NVM_RC_VERSION: string;
	export const FEATURES_CLICKHOUSE_HTTP_URL: string;
	export const LDFLAGS: string;
	export const MANPATH: string;
	export const INFERENCE_RESULTS_TOPIC: string;
	export const __MISE_DIFF: string;
	export const export: string;
	export const SSH_AGENT_PID: string;
	export const GHOSTTY_RESOURCES_DIR: string;
	export const TERM_PROGRAM: string;
	export const NODE: string;
	export const INIT_CWD: string;
	export const NVM_CD_FLAGS: string;
	export const CH_HTTP_URL: string;
	export const SHELL: string;
	export const TERM: string;
	export const KAFKA_PROCESSED_TOPIC: string;
	export const KAFKA_BOOTSTRAP_SERVERS: string;
	export const CPPFLAGS: string;
	export const TMPDIR: string;
	export const npm_config_global_prefix: string;
	export const FLINK_FEATURES_POWER_TOPIC: string;
	export const TERM_PROGRAM_VERSION: string;
	export const INFERENCE_ALERTS_ENABLED: string;
	export const CH_RAW_TABLE: string;
	export const FLINK_WATERMARK_LAG_MS: string;
	export const CH_KAFKA_BOOTSTRAP_SERVERS: string;
	export const FLINK_FEATURES_GROUP_ID: string;
	export const COLOR: string;
	export const npm_config_noproxy: string;
	export const FEATURES_KAFKA_GROUP_ID: string;
	export const FEATURES_CONSUMER_ENABLED: string;
	export const npm_config_local_prefix: string;
	export const ALERT_CLICKHOUSE_PASSWORD: string;
	export const KAFKA_PROCESSED_DLQ_TOPIC: string;
	export const PNPM_HOME: string;
	export const FEATURES_KAFKA_BOOTSTRAP_SERVERS: string;
	export const FLINK_FEATURES_ENV_TOPIC: string;
	export const MIX_ARCHIVES: string;
	export const NVM_DIR: string;
	export const USER: string;
	export const INFERENCE_ALERT_THRESHOLD: string;
	export const CH_DB: string;
	export const FEATURES_KAFKA_TOPIC: string;
	export const LS_COLORS: string;
	export const INFERENCE_ALERTS_GROUP_ID: string;
	export const COMMAND_MODE: string;
	export const npm_config_globalconfig: string;
	export const INFERENCE_CLICKHOUSE_PASSWORD: string;
	export const ALERT_CLICKHOUSE_USER: string;
	export const ALERT_WEBHOOK_URL: string;
	export const SSH_AUTH_SOCK: string;
	export const KAFKA_CONSUMER_ENABLED: string;
	export const __CF_USER_TEXT_ENCODING: string;
	export const npm_execpath: string;
	export const FEATURES_CLICKHOUSE_TABLE: string;
	export const PAGER: string;
	export const FEATURES_CLICKHOUSE_USER: string;
	export const INFERENCE_REQUESTS_TOPIC: string;
	export const KAFKA_DLQ_TOPIC: string;
	export const KAFKA_TOPIC: string;
	export const LSCOLORS: string;
	export const CH_FEATURE_POWER_TOPIC: string;
	export const INFERENCE_CLICKHOUSE_DB: string;
	export const FLINK_FEATURES_HEALTH_TOPIC: string;
	export const PATH: string;
	export const INFERENCE_RESULTS_GROUP_ID: string;
	export const CARGO_HOME: string;
	export const npm_package_json: string;
	export const ALERT_WEBHOOK_TIMEOUT_MS: string;
	export const CH_FEATURE_HEALTH_TOPIC: string;
	export const LaunchInstanceID: string;
	export const GHOSTTY_SHELL_FEATURES: string;
	export const npm_config_userconfig: string;
	export const npm_config_init_module: string;
	export const ALERT_CLICKHOUSE_TABLE: string;
	export const C_INCLUDE_PATH: string;
	export const __CFBundleIdentifier: string;
	export const npm_command: string;
	export const INFERENCE_CLICKHOUSE_HTTP_URL: string;
	export const PWD: string;
	export const INFERENCE_RESULTS_DLQ_TOPIC: string;
	export const FLINK_GROUP_ID: string;
	export const JAVA_HOME: string;
	export const npm_lifecycle_event: string;
	export const EDITOR: string;
	export const npm_package_name: string;
	export const LANG: string;
	export const INFERENCE_ALERTS_TOPIC: string;
	export const FLINK_INFERENCE_GROUP_ID: string;
	export const npm_config_npm_version: string;
	export const XPC_FLAGS: string;
	export const MIX_HOME: string;
	export const INFERENCE_ALERTS_DLQ_TOPIC: string;
	export const RUSTUP_TOOLCHAIN: string;
	export const npm_config_node_gyp: string;
	export const FLINK_INFERENCE_TOPIC: string;
	export const npm_package_version: string;
	export const FLINK_INPUT_TOPIC: string;
	export const XPC_SERVICE_NAME: string;
	export const CH_USER: string;
	export const HOME: string;
	export const SHLVL: string;
	export const CH_RESULT_TABLE: string;
	export const CH_ALERT_TOPIC: string;
	export const CH_FEATURE_TOPIC: string;
	export const __MISE_ORIG_PATH: string;
	export const TERMINFO: string;
	export const INFERENCE_KAFKA_BOOTSTRAP_SERVERS: string;
	export const RUSTUP_HOME: string;
	export const MISE_SHELL: string;
	export const FEATURES_KAFKA_DLQ_TOPIC: string;
	export const npm_config_cache: string;
	export const CH_ALERT_TABLE: string;
	export const CH_FEATURE_TABLE: string;
	export const KAFKA_SOURCE_TAG: string;
	export const LESS: string;
	export const LOGNAME: string;
	export const npm_lifecycle_script: string;
	export const FEATURES_CLICKHOUSE_PASSWORD: string;
	export const FLINK_FEATURES_BASE_TOPIC: string;
	export const FLINK_FEATURES_TOPIC: string;
	export const FLINK_RAW_TOPIC: string;
	export const XDG_DATA_DIRS: string;
	export const GEMINI_CLI: string;
	export const CH_FEATURE_ENV_TOPIC: string;
	export const CH_FEATURE_BASE_TOPIC: string;
	export const CH_RAW_TOPIC: string;
	export const KAFKA_GROUP_ID: string;
	export const GHOSTTY_BIN_DIR: string;
	export const CH_PASSWORD: string;
	export const CH_GROUP_ID: string;
	export const FLINK_KAFKA_BOOTSTRAP_SERVERS: string;
	export const PRODUCER_KIND: string;
	export const PKG_CONFIG_PATH: string;
	export const npm_config_user_agent: string;
	export const INFERENCE_CLICKHOUSE_TABLE: string;
	export const FLINK_SESSION_GAP_MS: string;
	export const __MISE_SESSION: string;
	export const ALERT_CLICKHOUSE_DB: string;
	export const INFERENCE_CLICKHOUSE_USER: string;
	export const CH_RESULT_TOPIC: string;
	export const OSLogRateLimit: string;
	export const GIT_PAGER: string;
	export const GEMINI_CLI_NO_RELAUNCH: string;
	export const FEATURES_CLICKHOUSE_DB: string;
	export const SECURITYSESSIONID: string;
	export const ALERT_CLICKHOUSE_HTTP_URL: string;
	export const ALERT_WEBHOOK_ENABLED: string;
	export const __MISE_ZSH_PRECMD_RUN: string;
	export const npm_node_execpath: string;
	export const npm_config_prefix: string;
	export const COLORTERM: string;
	export const _: string;
	export const NODE_ENV: string;
}

/**
 * This module provides access to environment variables that are injected _statically_ into your bundle at build time and are _publicly_ accessible.
 * 
 * |         | Runtime                                                                    | Build time                                                               |
 * | ------- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
 * | Private | [`$env/dynamic/private`](https://svelte.dev/docs/kit/$env-dynamic-private) | [`$env/static/private`](https://svelte.dev/docs/kit/$env-static-private) |
 * | Public  | [`$env/dynamic/public`](https://svelte.dev/docs/kit/$env-dynamic-public)   | [`$env/static/public`](https://svelte.dev/docs/kit/$env-static-public)   |
 * 
 * Static environment variables are [loaded by Vite](https://vitejs.dev/guide/env-and-mode.html#env-files) from `.env` files and `process.env` at build time and then statically injected into your bundle at build time, enabling optimisations like dead code elimination.
 * 
 * **_Public_ access:**
 * 
 * - This module _can_ be imported into client-side code
 * - **Only** variables that begin with [`config.kit.env.publicPrefix`](https://svelte.dev/docs/kit/configuration#env) (which defaults to `PUBLIC_`) are included
 * 
 * For example, given the following build time environment:
 * 
 * ```env
 * ENVIRONMENT=production
 * PUBLIC_BASE_URL=http://site.com
 * ```
 * 
 * With the default `publicPrefix` and `privatePrefix`:
 * 
 * ```ts
 * import { ENVIRONMENT, PUBLIC_BASE_URL } from '$env/static/public';
 * 
 * console.log(ENVIRONMENT); // => throws error during build
 * console.log(PUBLIC_BASE_URL); // => "http://site.com"
 * ```
 * 
 * The above values will be the same _even if_ different values for `ENVIRONMENT` or `PUBLIC_BASE_URL` are set at runtime, as they are statically replaced in your code with their build time values.
 */
declare module '$env/static/public' {
	
}

/**
 * This module provides access to environment variables set _dynamically_ at runtime and that are limited to _private_ access.
 * 
 * |         | Runtime                                                                    | Build time                                                               |
 * | ------- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
 * | Private | [`$env/dynamic/private`](https://svelte.dev/docs/kit/$env-dynamic-private) | [`$env/static/private`](https://svelte.dev/docs/kit/$env-static-private) |
 * | Public  | [`$env/dynamic/public`](https://svelte.dev/docs/kit/$env-dynamic-public)   | [`$env/static/public`](https://svelte.dev/docs/kit/$env-static-public)   |
 * 
 * Dynamic environment variables are defined by the platform you're running on. For example if you're using [`adapter-node`](https://github.com/sveltejs/kit/tree/main/packages/adapter-node) (or running [`vite preview`](https://svelte.dev/docs/kit/cli)), this is equivalent to `process.env`.
 * 
 * **_Private_ access:**
 * 
 * - This module cannot be imported into client-side code
 * - This module includes variables that _do not_ begin with [`config.kit.env.publicPrefix`](https://svelte.dev/docs/kit/configuration#env) _and do_ start with [`config.kit.env.privatePrefix`](https://svelte.dev/docs/kit/configuration#env) (if configured)
 * 
 * > [!NOTE] In `dev`, `$env/dynamic` includes environment variables from `.env`. In `prod`, this behavior will depend on your adapter.
 * 
 * > [!NOTE] To get correct types, environment variables referenced in your code should be declared (for example in an `.env` file), even if they don't have a value until the app is deployed:
 * >
 * > ```env
 * > MY_FEATURE_FLAG=
 * > ```
 * >
 * > You can override `.env` values from the command line like so:
 * >
 * > ```sh
 * > MY_FEATURE_FLAG="enabled" npm run dev
 * > ```
 * 
 * For example, given the following runtime environment:
 * 
 * ```env
 * ENVIRONMENT=production
 * PUBLIC_BASE_URL=http://site.com
 * ```
 * 
 * With the default `publicPrefix` and `privatePrefix`:
 * 
 * ```ts
 * import { env } from '$env/dynamic/private';
 * 
 * console.log(env.ENVIRONMENT); // => "production"
 * console.log(env.PUBLIC_BASE_URL); // => undefined
 * ```
 */
declare module '$env/dynamic/private' {
	export const env: {
		INFERENCE_RESULTS_ENABLED: string;
		CPLUS_INCLUDE_PATH: string;
		NVM_RC_VERSION: string;
		FEATURES_CLICKHOUSE_HTTP_URL: string;
		LDFLAGS: string;
		MANPATH: string;
		INFERENCE_RESULTS_TOPIC: string;
		__MISE_DIFF: string;
		export: string;
		SSH_AGENT_PID: string;
		GHOSTTY_RESOURCES_DIR: string;
		TERM_PROGRAM: string;
		NODE: string;
		INIT_CWD: string;
		NVM_CD_FLAGS: string;
		CH_HTTP_URL: string;
		SHELL: string;
		TERM: string;
		KAFKA_PROCESSED_TOPIC: string;
		KAFKA_BOOTSTRAP_SERVERS: string;
		CPPFLAGS: string;
		TMPDIR: string;
		npm_config_global_prefix: string;
		FLINK_FEATURES_POWER_TOPIC: string;
		TERM_PROGRAM_VERSION: string;
		INFERENCE_ALERTS_ENABLED: string;
		CH_RAW_TABLE: string;
		FLINK_WATERMARK_LAG_MS: string;
		CH_KAFKA_BOOTSTRAP_SERVERS: string;
		FLINK_FEATURES_GROUP_ID: string;
		COLOR: string;
		npm_config_noproxy: string;
		FEATURES_KAFKA_GROUP_ID: string;
		FEATURES_CONSUMER_ENABLED: string;
		npm_config_local_prefix: string;
		ALERT_CLICKHOUSE_PASSWORD: string;
		KAFKA_PROCESSED_DLQ_TOPIC: string;
		PNPM_HOME: string;
		FEATURES_KAFKA_BOOTSTRAP_SERVERS: string;
		FLINK_FEATURES_ENV_TOPIC: string;
		MIX_ARCHIVES: string;
		NVM_DIR: string;
		USER: string;
		INFERENCE_ALERT_THRESHOLD: string;
		CH_DB: string;
		FEATURES_KAFKA_TOPIC: string;
		LS_COLORS: string;
		INFERENCE_ALERTS_GROUP_ID: string;
		COMMAND_MODE: string;
		npm_config_globalconfig: string;
		INFERENCE_CLICKHOUSE_PASSWORD: string;
		ALERT_CLICKHOUSE_USER: string;
		ALERT_WEBHOOK_URL: string;
		SSH_AUTH_SOCK: string;
		KAFKA_CONSUMER_ENABLED: string;
		__CF_USER_TEXT_ENCODING: string;
		npm_execpath: string;
		FEATURES_CLICKHOUSE_TABLE: string;
		PAGER: string;
		FEATURES_CLICKHOUSE_USER: string;
		INFERENCE_REQUESTS_TOPIC: string;
		KAFKA_DLQ_TOPIC: string;
		KAFKA_TOPIC: string;
		LSCOLORS: string;
		CH_FEATURE_POWER_TOPIC: string;
		INFERENCE_CLICKHOUSE_DB: string;
		FLINK_FEATURES_HEALTH_TOPIC: string;
		PATH: string;
		INFERENCE_RESULTS_GROUP_ID: string;
		CARGO_HOME: string;
		npm_package_json: string;
		ALERT_WEBHOOK_TIMEOUT_MS: string;
		CH_FEATURE_HEALTH_TOPIC: string;
		LaunchInstanceID: string;
		GHOSTTY_SHELL_FEATURES: string;
		npm_config_userconfig: string;
		npm_config_init_module: string;
		ALERT_CLICKHOUSE_TABLE: string;
		C_INCLUDE_PATH: string;
		__CFBundleIdentifier: string;
		npm_command: string;
		INFERENCE_CLICKHOUSE_HTTP_URL: string;
		PWD: string;
		INFERENCE_RESULTS_DLQ_TOPIC: string;
		FLINK_GROUP_ID: string;
		JAVA_HOME: string;
		npm_lifecycle_event: string;
		EDITOR: string;
		npm_package_name: string;
		LANG: string;
		INFERENCE_ALERTS_TOPIC: string;
		FLINK_INFERENCE_GROUP_ID: string;
		npm_config_npm_version: string;
		XPC_FLAGS: string;
		MIX_HOME: string;
		INFERENCE_ALERTS_DLQ_TOPIC: string;
		RUSTUP_TOOLCHAIN: string;
		npm_config_node_gyp: string;
		FLINK_INFERENCE_TOPIC: string;
		npm_package_version: string;
		FLINK_INPUT_TOPIC: string;
		XPC_SERVICE_NAME: string;
		CH_USER: string;
		HOME: string;
		SHLVL: string;
		CH_RESULT_TABLE: string;
		CH_ALERT_TOPIC: string;
		CH_FEATURE_TOPIC: string;
		__MISE_ORIG_PATH: string;
		TERMINFO: string;
		INFERENCE_KAFKA_BOOTSTRAP_SERVERS: string;
		RUSTUP_HOME: string;
		MISE_SHELL: string;
		FEATURES_KAFKA_DLQ_TOPIC: string;
		npm_config_cache: string;
		CH_ALERT_TABLE: string;
		CH_FEATURE_TABLE: string;
		KAFKA_SOURCE_TAG: string;
		LESS: string;
		LOGNAME: string;
		npm_lifecycle_script: string;
		FEATURES_CLICKHOUSE_PASSWORD: string;
		FLINK_FEATURES_BASE_TOPIC: string;
		FLINK_FEATURES_TOPIC: string;
		FLINK_RAW_TOPIC: string;
		XDG_DATA_DIRS: string;
		GEMINI_CLI: string;
		CH_FEATURE_ENV_TOPIC: string;
		CH_FEATURE_BASE_TOPIC: string;
		CH_RAW_TOPIC: string;
		KAFKA_GROUP_ID: string;
		GHOSTTY_BIN_DIR: string;
		CH_PASSWORD: string;
		CH_GROUP_ID: string;
		FLINK_KAFKA_BOOTSTRAP_SERVERS: string;
		PRODUCER_KIND: string;
		PKG_CONFIG_PATH: string;
		npm_config_user_agent: string;
		INFERENCE_CLICKHOUSE_TABLE: string;
		FLINK_SESSION_GAP_MS: string;
		__MISE_SESSION: string;
		ALERT_CLICKHOUSE_DB: string;
		INFERENCE_CLICKHOUSE_USER: string;
		CH_RESULT_TOPIC: string;
		OSLogRateLimit: string;
		GIT_PAGER: string;
		GEMINI_CLI_NO_RELAUNCH: string;
		FEATURES_CLICKHOUSE_DB: string;
		SECURITYSESSIONID: string;
		ALERT_CLICKHOUSE_HTTP_URL: string;
		ALERT_WEBHOOK_ENABLED: string;
		__MISE_ZSH_PRECMD_RUN: string;
		npm_node_execpath: string;
		npm_config_prefix: string;
		COLORTERM: string;
		_: string;
		NODE_ENV: string;
		[key: `PUBLIC_${string}`]: undefined;
		[key: `${string}`]: string | undefined;
	}
}

/**
 * This module provides access to environment variables set _dynamically_ at runtime and that are _publicly_ accessible.
 * 
 * |         | Runtime                                                                    | Build time                                                               |
 * | ------- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
 * | Private | [`$env/dynamic/private`](https://svelte.dev/docs/kit/$env-dynamic-private) | [`$env/static/private`](https://svelte.dev/docs/kit/$env-static-private) |
 * | Public  | [`$env/dynamic/public`](https://svelte.dev/docs/kit/$env-dynamic-public)   | [`$env/static/public`](https://svelte.dev/docs/kit/$env-static-public)   |
 * 
 * Dynamic environment variables are defined by the platform you're running on. For example if you're using [`adapter-node`](https://github.com/sveltejs/kit/tree/main/packages/adapter-node) (or running [`vite preview`](https://svelte.dev/docs/kit/cli)), this is equivalent to `process.env`.
 * 
 * **_Public_ access:**
 * 
 * - This module _can_ be imported into client-side code
 * - **Only** variables that begin with [`config.kit.env.publicPrefix`](https://svelte.dev/docs/kit/configuration#env) (which defaults to `PUBLIC_`) are included
 * 
 * > [!NOTE] In `dev`, `$env/dynamic` includes environment variables from `.env`. In `prod`, this behavior will depend on your adapter.
 * 
 * > [!NOTE] To get correct types, environment variables referenced in your code should be declared (for example in an `.env` file), even if they don't have a value until the app is deployed:
 * >
 * > ```env
 * > MY_FEATURE_FLAG=
 * > ```
 * >
 * > You can override `.env` values from the command line like so:
 * >
 * > ```sh
 * > MY_FEATURE_FLAG="enabled" npm run dev
 * > ```
 * 
 * For example, given the following runtime environment:
 * 
 * ```env
 * ENVIRONMENT=production
 * PUBLIC_BASE_URL=http://example.com
 * ```
 * 
 * With the default `publicPrefix` and `privatePrefix`:
 * 
 * ```ts
 * import { env } from '$env/dynamic/public';
 * console.log(env.ENVIRONMENT); // => undefined, not public
 * console.log(env.PUBLIC_BASE_URL); // => "http://example.com"
 * ```
 * 
 * ```
 * 
 * ```
 */
declare module '$env/dynamic/public' {
	export const env: {
		[key: `PUBLIC_${string}`]: string | undefined;
	}
}
