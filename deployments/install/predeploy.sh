#!/usr/bin/env sh

if [ ! -f ./atp-common-scripts/openshift/common.sh ]; then
  echo "ERROR: Cannot locate ./atp-common-scripts/openshift/common.sh"
  exit 1
fi

# shellcheck source=../atp-common-scripts/openshift/common.sh
. ./atp-common-scripts/openshift/common.sh

_ns="${NAMESPACE}"
if [ "${EI_GRIDFS_ENABLED:-true}" = "true" ]; then
  echo "***** Preparing MongoDB connection *****"
  EDS_GRIDFS_DB="$(env_default "${EDS_GRIDFS_DB}" "itf_eds_gridfs" "${_ns}")"
  EDS_GRIDFS_USER="$(env_default "${EDS_GRIDFS_USER}" "itf_eds_gridfs" "${_ns}")"
  EDS_GRIDFS_PASSWORD="$(env_default "${EDS_GRIDFS_PASSWORD}" "itf_eds_gridfs" "${_ns}")"
  echo "circly"
  echo "***** Initializing databases ******"
  init_mongo "${MONGO_DB_ADDR}" "${EDS_GRIDFS_DB}" "${EDS_GRIDFS_USER}" "${EDS_GRIDFS_PASSWORD}" "${MONGO_DB_PORT}"  "${mongo_user}" "${mongo_pass}"
fi
