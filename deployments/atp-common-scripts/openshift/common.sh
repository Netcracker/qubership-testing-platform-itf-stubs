#!/usr/bin/env sh

[ "${DEBUG}" ] && set -x

VAULT_NAMESPACE="${VAULT_NAMESPACE:-atp}"
SECRETS_ROOT="${SECRETS_ROOT:-kv}"
AES_KEY=""
PG_EXTENSIONS=""
RESERVED_PORTS="${RESERVED_PORTS}"
MONGO_BINARY_PATH="${MONGO_BINARY_PATH:-/opt/mongodb/bin}"
ATP_CLOUD_DEPLOY="${ATP_CLOUD_DEPLOY:-true}"
ATP_CRYPTO_KEY_DEV="{ENC}{}{Sck4jAe1F2+uknItF3x4gS6jKaghLUPaYL9+FCip8xxB0R/3vfzbG70rBrC7/utroXr4bdyzICWTxJ+mQHZwBCcEt0JENU1rwoN2z9Y9Q/hfL6agLYSxuc1w2yFMM8MU8fJyrA5586cfMtCi3f5wHzh7WljjcsB8J6CptbCKC7PNoIdAa8VX2DhvRIReWsLrhhe1bbzl/GhqhqIf9Gr2CALUsAZwnv+NyfjTVExuWJWdDP0BS8gnlAlVJyQZGiYJmrsNsNRhC1Rhhg59jvDv9sm+zBUw81G62w+JJP+36XOnRIuuSC6RxckrypQFM04a+XolV6KuhShhoW+zv2IlwQ==}"
ATP_CRYPTO_PRIVATE_KEY_DEV="MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCSs7+T9jYks2wplAIkKZAQZWSzD6sDnfNnkkAHAe4eh1E4OQDyc+Gay11QCxaM0VFwl3JHeFst/fXYMJNsAh7kX00arNDb6QXABoilKDtFY4FPzXJlG6JmLjzJhREl2cH4V5mar1Wb7NQgWeHOBOfYnnwsKUKMI2wRgEhJnkQl5cFFElGikanyAPTq1YTfLVG3ijXDVzI/acSCv/FPEVlyXB0TpolkIOY5KWkDRkNN28Q22ED1CWAw7NV8o9Hgmo+z1gwSDk75ms9zpS/P38LMj0aqhC0ixzQ+WFmf5ODGAE+kShk3JZ7rUmj4PewnyfdAxE6ib80t0z23J1dm8TLdAgMBAAECggEAZ04YjFMHEZUfh6/oShrSjhk4bjeMT8G8k6STXdvoGKtlcFgD6LfdmKm4jhMg0AzecpDTMqz4WEzMAG9EgPyFUIAjxbMIaLORDwYK13KbOmO1vcKI2dY56AaLW8VOq/7J7t2RFzJ88I43Woiwz+j4crw36MktSY3wHerd/Klsh9pO0bPjGLQsSEcJk6BiEjB+hVqBSZa3ioA4uvTP48Tr8T+eLTNu81NI4XeibwmRypyUr4FrAYLubMOYoIO2H9XC68dO6whjmiJ3UeAE/TQ31OipZz74vzwNuT0zYLo3kWqxVesLp1ia/q9nqjYinXqcDJIv1fEBZU5BE4NsgZPZwQKBgQDUEZQy0k1Si+gafcFFBfxnGpnDWSosAgi7ytXvt8Y2YNo3VgNOZ1ykf9bqZezckMo2kPcy5CUEjQHxzjGqq+Ot5qYMyfh0vZ/h8X89IVPxwiDylPhUU0QNk4G9X8Sbw8z+z6tb98++wYbtVCcYFeYNZ4MWYI1usjfBpkYPZYZ/CQKBgQCxF6c7M1IPPUB15bfhWv2ihf2Z5hC/+/JIytUT54XOFd6xZ5TlUwudJcQIpUruCDJilzZBicaA8N4AUKoNtr1eNDf1gXJO+eyyZXN4SH6WIpSXP9MMxmNtX/3H356NyfkqpAZtteMjDfIwW4L7UtF0iuI0zmvxCUhxJoWqLyU2NQKBgFiANY64ARjP1j8n9/4sL1d/3GeP0G+pMafdUEbINOoApVCujparwBfOWgxcGOs7aYg4G1GbsG8jwYn9+PA2579tICL6LrvZXt3WALmsLPIZh9J0pOXcEexwgJZdXxl6LxSv6d1pn8MF1J86nU4J5YX2ithN1vg5W9du4pIOVoCxAoGBAJfVzd4mLE9Alwn+gV/IYfp8o2jWJrpUS/E5ZuN/9+swORUl2DWetDBydtdq0QmxIXICb9RVSkq3OcBPaN4FNeuVHf1ylQ09n0F9Vjlk/pO+5mOfp1YmqozWZoJ+KjUrXGTA6XobHrmpdWMcsvrEkS04/qWD7mxlJyVMgAHgFimZAoGBANIatOQqXT3i4rfvfN8JeFA0RbOAFXqbMb8Ty0IhVEdvj4MNYhRwmaFnW/my2WQcldufGM5FjWbSc4/cEuVp1q4ybhQ8q3XbZQpdRrt5PlZPxgN7ctxTcTI67d7/I+Rf6io+fKaOrPnxHdJqjQj9kNbYG5iAZ20GbR7aCGVuKEaE"

export cloud_client="kubectl"

# Print messages (if any) and exit with error code 1
# $1..$n - messages
panic() {
  while [ ${#} -gt 0 ]; do
    echo "PANIC: $1"
    shift
  done
  exit 1
}

# Force create a secret or a config map
# $1 - type: secret or configmap
# $2 - name
# $3 - source flags, e.g. --from-literal or --from-file
# $4 - message (optional)
recreate() {
  echo "${4:-=> Recreating $1 $2 ...}"
  case ${1:?Empty type} in
    "secret") eval "${cloud_client} create ${1} generic ${2:?Empty name} ${3:?Empty source} --dry-run -o yaml | ${cloud_client} apply -f - " ;;
    "configmap") eval "${cloud_client} create ${1} ${2:?Empty name} ${3:?Empty source} --dry-run -o yaml | ${cloud_client} apply -f - " ;;
    *) panic "Unsupported type '${1}'. Expected: secret, configmap" ;;
  esac
}

# Create a secret or a config map if it doesn't exist
# $1 - type: secret or configmap
# $2 - name
# $3 - source flags, e.g. --from-literal or --from-file
create() {
  echo "=> Checking ${2} ..."
  if ${cloud_client} get ${1:?Empty type} ${2:?Empty name}; then
    echo "Specified ${1} '${2}' exists, nothing to do"
  else
    recreate "${1}" "${2}" "${3}" "Specified ${1} '${2}' is absent, creating new"
  fi
}

# Run KeyPairGenerator
# $1 - AES key (optional)
atp_crypt() {
  if [ "${ATP_CLOUD_DEPLOY}" = "false" ] ; then
    _img="$(docker-compose config --images 2>/dev/null | head -n1)"
    _cmd="docker run --rm --entrypoint=/bin/sh ${_img}"
  fi
  ${_cmd:-sh} -c "java -cp \"./lib/*\" org.qubership.atp.crypt.KeyPairGenerator ${1} 2>/dev/null"
}

# Extract value by given key from atp_crypt output
# $1 - key
# $2 - atp_crypt output (optional)
extract_key() {
  _key="${1:?Empty key}"
  _output="${2:-$(atp_crypt)}"
  echo "${_output}" | grep -m 1 "^${_key}=" | sed "s/${_key}=//"
}

# Send GET request to Vault
# Require global variables VAULT_ADMIN_TOKEN, SECRETS_ROOT, VAULT_NAMESPACE, VAULT_URI
# $1 - service name or endpoint
get_vault() {
  _ep="${SECRETS_ROOT:?}/${VAULT_NAMESPACE:?}-${1:?Empty endpoint}"
  if echo "${1}" | grep -qF "/"; then
    _ep="${1}"
  fi
  curl -ksS -X GET -H "X-Vault-Token: ${VAULT_ADMIN_TOKEN:?}" ${VAULT_URI:?}/v1/${_ep}
}

# Send POST request to Vault
# Require global variables VAULT_ADMIN_TOKEN, SECRETS_ROOT, VAULT_NAMESPACE, VAULT_URI
# $1 - service name or endpoint
# $2 - request body
post_vault() {
  _ep="${SECRETS_ROOT:?}/${VAULT_NAMESPACE:?}-${1:?Empty endpoint}"
  if echo "${1}" | grep -qF "/"; then
    _ep="${1}"
  fi
  echo "${2:?Empty body}" | curl -ksS -X POST -H "X-Vault-Token: ${VAULT_ADMIN_TOKEN:?}" ${VAULT_URI:?}/v1/${_ep} -d @-
}

# Verify if expression is null (literally) or empty
# $1 - expression
is_null() {
  [ -z "${1}" ] || [ "${1}" = "null" ]
}

# Check AES key in atp-crypto-secrets and generate a new one, if doesn't exist.
# Save AES key value into global variable AES_KEY
check_aes_secret() {
  echo "=> Checking AES key in secret ..."
  if ${cloud_client} get secret atp-crypto-secrets; then
    echo "* AES key exists, using current"
    AES_KEY="$(${cloud_client} get secret atp-crypto-secrets -o json | jq -r '.data.AES_KEY | @base64d')"
  else
    echo "* AES key is absent, generating a new one"
    AES_KEY="$(extract_key "key")"
    recreate "secret" "atp-crypto-secrets" "--from-literal=AES_KEY=${AES_KEY:?}"
  fi
}

# Check AES key in Vault and generate a new one, if doesn't exist
# Require global variables VAULT_ADMIN_TOKEN, SECRETS_ROOT, VAULT_NAMESPACE, VAULT_URI
# Save AES key value into global variable AES_KEY
check_vault_secret() {
  _ep="${SECRETS_ROOT:?}/${VAULT_NAMESPACE:?}-atp-crypto-secrets"
  echo "=> Checking AES key by path '${_ep}' ..."
  _key="$(get_vault "${_ep}" | jq -r ".data.aes_key")"
  if is_null "${_key}"; then
    echo "* AES key is absent, generating a new one"
    AES_KEY="$(extract_key "key")"
    post_vault "${_ep}" "{\"aes_key\":\"${AES_KEY:?}\"}"
  else
    echo "* AES key exists, using current"
    AES_KEY=${_key}
  fi
}

# Check AES key in a local file and generate a new one, if doesn't exist.
# Save AES key value into global variable AES_KEY
check_aes_file() {
  _kp="${AES_KEY_PATH:-../.secret}"
  echo "=> Checking Key by path '${_kp:?Key path is empty}' ..."
  if [ -s ${_kp} ]; then
    echo "* Key found, using it"
    AES_KEY="$(cat "${_kp}")"
  else
    echo "* Key is absent, creating a new one"
    AES_KEY="$(extract_key "key")"
    echo "${AES_KEY:?Empty Key}" >"${_kp}"
    chmod 600 "${_kp}"
  fi
}

# Update secret with provided key=value pairs
# $1 - secret name
# $2 - either to keep existing value or override [true|false]
# $3..$n - key=value pairs
update_secret() {
  if [ $# -lt 3 ]; then
    panic "Not enough arguments"
  fi
  _secret="${1:?Empty secret name}"
  _keep="${2?Empty keep flag}"
  shift 2
  _source="$(${cloud_client} get secret ${_secret} -o json | jq -r '.data | map_values(. // "" | @base64d)')"
  while [ ${#} -gt 0 ]; do
    _k="${1%%=*}"
    _v="${1#*=}"
    _source="$(echo "${_source}" |
      jq -r 'if has("'${_k}'") and ('${_keep}' and .'${_k}' != "") then . else (.'${_k}'="'${_v}'") end')"
    shift
  done
  _source="$(echo "${_source}" |
    jq -r '[ keys[] as $k | "--from-literal=\"\($k)=\(.[$k])\"" ] | join(" ")')"
  recreate "secret" "${_secret}" "${_source}"
}

# Generate public and private keys based on provided
# AES key and add them to specified service secret
# $1 - AES key
# $2 - secret name
generate_keys_secret() {
  echo "=> Generating pair of keys in secrets mode ..."
  _keys="$(atp_crypt "${1:?Empty AES key}")"
  _secret="${2:?Empty secret name}"
  _ek="$(extract_key "encryptedKey" "${_keys:?}")"
  _pk="$(extract_key "privateKey" "${_keys:?}")"
  update_secret "${_secret}" "false" "ATP_CRYPTO_KEY=${_ek:?}" "ATP_CRYPTO_PRIVATE_KEY=${_pk:?}"
}

# Generate public and private keys based on provided
# AES key and add them to Vault service
# $1 - AES key
# $2 - service name
generate_keys_vault() {
  echo "=> Generating pair of keys in Vault mode ..."
  _keys="$(atp_crypt "${1:?Empty AES key}")"
  _ek="$(extract_key "encryptedKey" "${_keys:?}")"
  _pk="$(extract_key "privateKey" "${_keys:?}")"
  post_vault "${2}" "{\"atp.crypto.key\":\"${_ek:?}\",\"atp.crypto.privateKey\":\"${_pk:?}\"}"
}

# Add pre-generated DEV keys to specified service secret
# $1 - secret
generate_keys_dev() {
  echo "=> Generating pair of keys in DEV mode ..."
  _secret="${1:?Empty secret name}"
  update_secret "${_secret}" "true" "ATP_CRYPTO_KEY=${ATP_CRYPTO_KEY_DEV}" "ATP_CRYPTO_PRIVATE_KEY=${ATP_CRYPTO_PRIVATE_KEY_DEV}"
}

# Generate public and private keys based on provided
# AES key and add store them into a local file
# $1 - AES key
# $2 - storage path (optional)
generate_keys_compose() {
  _storage="${2:-.secret}"
  echo "=> Generating pair of keys in Compose mode ..."
  _keys="$(atp_crypt "${1:?Empty AES key}")"
  _ek="$(extract_key "encryptedKey" "${_keys:?}")"
  _pk="$(extract_key "privateKey" "${_keys:?}")"
  echo "ATP_CRYPTO_KEY=${_ek:?}" >"${_storage}"
  echo "ATP_CRYPTO_PRIVATE_KEY=${_pk:?}" >>"${_storage}"
  chmod 600 "${_storage}" || true
}

# Add service policies to access secrets
# Require global variables VAULT_ADMIN_TOKEN, SECRETS_ROOT, VAULT_NAMESPACE, VAULT_URI
# SECRET_ID - global env variable, should be defined before function launch
# $1 - service name
# $2 - role ID (optional, default is $1)
add_service_to_vault() {
  _service="${1:?Empty service name}"
  _role="${2:-${_service}}"
  _policy="$(echo "${VAULT_NAMESPACE:?}.${_service}" | sed 's#/#.#g')"
  echo "=> Checking policy at '${_policy}' ..."
  if ! is_null "$(get_vault "auth/approle/role/${_policy:?}" | jq -r '.data')"; then
    echo "* Policy exists, skipping creation"
  else
    echo "* Policy doesn't exist, creating new"
    curl -ksS -X PUT -H "X-Vault-Token: ${VAULT_ADMIN_TOKEN:?}" ${VAULT_URI:?}/v1/sys/policies/acl/${_policy:?} -d @- <<EOF
    { "policy":
      " path \"${SECRETS_ROOT:?}/${VAULT_NAMESPACE:?}-${_service}/*\"
          { capabilities = [\"create\", \"read\", \"update\", \"list\"] }
        path \"${SECRETS_ROOT:?}/${VAULT_NAMESPACE:?}-${_service}\"
          { capabilities = [\"create\", \"read\", \"update\", \"list\"] } "
    }
EOF
    post_vault "auth/approle/role/${_policy:?}" \
      "{\"role_id\":\"${_role:?}\",\"token_policies\":\"${_policy:?}\",\"token_ttl\":\"1h\",\"token_max_ttl\":\"4h\"}"

    if is_null "$(get_vault "auth/approle/role/${_policy:?}" | jq -r '.data')"; then
      panic "Can't create new policy and role"
    fi
  fi
  echo "=> Requesting secret ID ..."
  _secret_id="$(post_vault "auth/approle/role/${_policy:?}/secret-id" "{}" | jq -r '.data.secret_id')"
  if is_null "${_secret_id}"; then
    panic "Got empty secret ID"
  fi
  SECRET_ID="${_secret_id}"
}

# $1 - encryption mode [secrets|vault|dev]
# $2 - service name / Vault role ID
# $3 - secret name (optional)
encrypt() {
  echo "ENCRYPT: ${1}"
  _service="${2:?Empty service name}"
  _secret="${3:-${_service}-secrets}"
  case ${1} in
    "secrets")
      check_aes_secret
      generate_keys_secret "${AES_KEY}" "${_secret}"
      update_secret "${_secret}" "true" "VAULT_SECRET_ID="
      ;;
    "vault")
      add_service_to_vault "${_service}" "${VAULT_ROLE_ID}"
      if [ -z "${SECRET_ID}" ] ; then
        panic "SECRET_ID was not created"
      fi
      check_vault_secret
      generate_keys_vault "${AES_KEY}" "${_service}"
      update_secret "${_secret}" "false" "VAULT_SECRET_ID=${SECRET_ID}"
      update_secret "${_secret}" "true" "ATP_CRYPTO_KEY=" "ATP_CRYPTO_PRIVATE_KEY="
      ;;
    "dev")
      generate_keys_dev "${_secret}"
      update_secret "${_secret}" "true" "VAULT_SECRET_ID="
      ;;
    "compose")
      check_aes_file
      generate_keys_compose "${AES_KEY}"
      ;;
    *)
      panic "Unsupported type '${1}'. Expected: secrets, vault, dev, compose"
      ;;
  esac
}

# Look for a first free local port and append to RESERVED_PORTS variable
free_local_port() {
  read -r _start _end </proc/sys/net/ipv4/ip_local_port_range
  for port in $(seq ${_start} ${_end}); do
    if echo "${RESERVED_PORTS}" | grep -qvw "${port}" &&
       netstat -ltn4 | grep -qvF ":${port} "; then
      RESERVED_PORTS="${RESERVED_PORTS} ${port}"
      echo "${port}"
      break
    fi
  done
}

# Execute a command to check connection
# $1 - number of attempts
# $2 - check command
# $3 - sleep interval (optional)
test_connection() {
  attempts=${1:-1}
  while ! eval "${2:?Empty check}"; do
    attempts=$((attempts - 1))
    echo "Connection attempt failed. Attempts left: ${attempts}"
    if [ ${attempts} -le 0 ]; then
      return 1
    fi
    sleep ${3:-5s}
  done
  echo "Connection succeeded."
}

# Run migration scripts connected as service user
# $1 - command (for SQL migration only)
# $2 - migration dist artifact
# $3 - host
# $4 - port (forwarded or direct)
# $5 - database name
# $6 - user (optional)
# $7 - password (optional)
migrate() {
  _cmd="${1}"
  _dist="${2:?Empty migration artifact}"
  _host="${3:?Empty host}"
  _port="${4:?Empty port}"
  _db="${5:?Empty database}"
  _user="${6:-$5}"
  _pass="${7:-$5}"
  if echo "${_dist}" | grep -Eq '^http(s)?://'; then
    wget --no-check-certificate -nv ${_dist}
    _dist="${_dist##*/}"
  fi
  if [ ! -f ${_dist} ]; then
    panic "Migration artifact '${_dist}' not found!"
  fi
  _target="${_dist%.zip}"
  _home="${_target}/db-migration"
  if [ -d ${_target} ]; then
    rm -rf ${_target}
  fi
  unzip ${_dist} -d ${_target}
  if [ ! -d ${_home} ]; then
    find ${_target} -type f -iname '*.sql' | while read -r f; do
      echo "=> Running script ${f} ..."
      ${_cmd} bash -sc "PGPASSWORD='${_pass}' psql -b -h \"${_host}\" -p \"${_port}\" -d \"${_db}\" -U \"${_user}\"" <"${f}"
    done
  fi
}

# Create a user, database and uuid-ossp extenstion as specified
# $1 - command
# $2 - database
# $3 - username
# $4 - password
# $5 - host
# $6 - port
# $7 - admin user
# $8 - admin password (optional)
create_pg_db() {
  _cmd="${1}"
  _db="${2:?Empty database}"
  _user="${3:?Empty user}"
  _pass="${4:?Empty password}"
  _host="${5:?Empty host}"
  _port="${6:?Empty port}"
  _as="${7:?Empty admin user}"
  # shellcheck disable=2016
  _pw="${8:+PGPASSWORD='$8'}"
  echo "=> Creating database '${_db}' ..."
  ${_cmd} bash -c "${_pw} psql -h \"${_host}\" -p \"${_port}\" -U \"${_as}\" -c \"CREATE USER \"${_user}\" WITH ENCRYPTED PASSWORD '${_pass}'\""
  ${_cmd} bash -c "${_pw} psql -h \"${_host}\" -p \"${_port}\" -U \"${_as}\" -c \"CREATE DATABASE \"${_db}\" OWNER \"${_user}\"\""
  ${_cmd} bash -c "${_pw} psql -h \"${_host}\" -p \"${_port}\" -U \"${_as}\" -c \"GRANT ALL PRIVILEGES ON DATABASE \"${_db}\" TO \"${_user}\"\""
  for ext in "uuid-ossp" ${PG_EXTENSIONS}; do
    ${_cmd} bash -c "${_pw} psql -h \"${_host}\" -p \"${_port}\" -U \"${_as}\" -d \"${_db}\" -c 'CREATE EXTENSION IF NOT EXISTS \"${ext}\"'"
  done
}

# Create a user in mongodb and grant role in the given database
# $1 - command
# $2 - host
# $3 - database
# $4 - username
# $5 - password
# $6 - port
# $7 - admin user
# $8 - admin password
create_mongo_db() {
  _cmd="${1}"
  _host="${2:?Empty host}"
  _db="${3:?Empty database}"
  _user="${4:?Empty user}"
  _pass="${5:?Empty password}"
  _port="${6:?Empty port}"
  _as="${7:?Empty admin user}"
  _pw="${8:?Empty admin password}"
  echo "=> Creating database '${_db}' ..."
  ${_cmd} sh -c "PATH=${MONGO_BINARY_PATH}:\$PATH mongo mongodb://${_as}:${_pw}@${_host}:${_port}/?authSource=admin --eval \"d = db.getSiblingDB('${_db}'); if(d.getUser('${_user}') == null) d.createUser({user: '${_user}', pwd: '${_pass}', roles: [{ role: 'readWrite', db: '${_db}'}]}); else print('User ${_user} exists');\""
}

# Connect to a mongos instance and create user if absent
# $1 - hostname
# $2 - database
# $3 - user (optional)
# $4 - pass (optional)
# $5 - port (optional)
# $6 - admin user (optional)
# $7 - admin password (optional)
init_mongo() {
  _host="${1:?Empty host}"
  _db="${2:?Empty database}"
  _user="${3:-$_db}"
  _pass="${4:-$_user}"
  _port="${5:-27017}"
  _as="${6:-root}"
  _pw="${7:-root}"
  echo "Mongo host: ${_host}"
  if [ "${ATP_CLOUD_DEPLOY}" = "false" ]; then
    _cmd="docker exec mongo"
    if [ "${USE_LOCAL_MONGO:-false}" = "true" ] && command -v mongo; then
      _cmd="eval"
    fi
  fi
  create_mongo_db "${_cmd}" "${_host}" "${_db}" "${_user}" "${_pass}" "${_port}" "${_as}" "${_pw}"
}

# Connect to a PG instance and initialize database, user etc.
# $1 - hostname
# $2 - database
# $3 - user (optional)
# $4 - pass (optional)
# $5 - port (optional)
# $6 - admin user (optional)
# $7 - admin password (optional)
# $8 - migration dist (optional)
# $9 - creation flag (optional)
init_pg() {
  _host="${1:?Empty host}"
  _db="${2:?Empty database}"
  _user="${3:-$_db}"
  _pass="${4:-$_user}"
  _port="${5:-5432}"
  _as="${6:-postgres}"
  _pw="${7}"
  _migration="${8}"
  _creation="${9:-true}"
  if [ "${ATP_CLOUD_DEPLOY}" = "false" ]; then
    echo "Treating ${_host} as a plain host ..."
    _cmd="docker exec -i postgres"
    if [ "${USE_LOCAL_PSQL:-false}" = "true" ] && command -v psql; then
      _cmd="eval"
    fi
  else
    echo "PG host: ${_host}"
    _cmd=""
  fi
  if [ "${_creation}" = "true" ]; then
     create_pg_db "${_cmd}" "${_db}" "${_user}" "${_pass}" "${_host}" "${_port}" "${_as}" "${_pw}"
  fi
  if [ "${_migration}" ]; then
    migrate "${_cmd}" "${_migration}" "${_host}" "${_port}" "${_db}" "${_user}" "${_pass}"
  fi
}

# Run migration dist using specified connection parameters
# $1 - hostname
# $2 - database
# $3 - user
# $4 - pass
# $5 - port
# $6 - migration dist
# $7 - admin user (optional)
# $8 - admin password (optional)
migrate_pg() {
  init_pg "${1}" "${2}" "${3}" "${4}" "${5}" "${7}" "${8}" "${6:?Empty migration dist}"
}

# Calculate a new value if provided one is empty
# $1 - original value to check
# $2 - default value if original one is empty
# $3 - prefix for default value (optional)
env_default() {
  if [ "${1}" ]; then
    echo "${1}"
  else
    _def="${2:?Empty default value}"
    if [ "${3}" ]; then
      _def="${3}_${_def#atp-}"
    fi
    echo "${_def}" | sed "s/ //g;s/-/_/g"
  fi
}