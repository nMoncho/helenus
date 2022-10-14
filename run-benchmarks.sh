#!/usr/bin/env bash

## Formatting constants
BOLD='\033[1m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

cd "$(dirname "$0")" # change to script folder

shopt -s globstar # allows to list files recursively

REPORT_FOLDER="reports"
UNIFIED_REPORT_NAME="helenus-benchmark"
UNIFIED_REPORT=false
REPORT_FORMAT="csv"

mkdir -p "bench/${REPORT_FOLDER}"

separate_reports () {
  echo -e "Running ${BOLD}separate${NC} benchmark report\nThis will take some time..."
  read -p "Press any key to continue..."
  for bench in bench/**/*Benchmark.scala
  do
    CLASSNAME=$(echo "${bench}" | \
      # remove extension
      sed 's/.scala//g' | \
      # remove path to file
      perl -nE 'say join "", (split /\//)[-1]')

    FILENAME=$(echo "${CLASSNAME}"| \
      # to dash file: 'ByteCodecBenchmark' -> 'byte-codec-benchmark'
      perl -nE 'say join "-", map {lc} split(/(?=[[:upper:]])/)')

    sbt "bench/jmh:run -rff ${REPORT_FOLDER}/${FILENAME}.${REPORT_FORMAT} -rf ${REPORT_FORMAT} .*${CLASSNAME}"
  done
}

unified_report () {
  echo -e "Running ${BOLD}unified${NC} benchmark report\nThis will take some time..."
  read -p "Press any key to continue"
  sbt "bench/jmh:run -rff ${REPORT_FOLDER}/${UNIFIED_REPORT_NAME}.${REPORT_FORMAT} -rf ${REPORT_FORMAT}"
}

print_help () {
  cat << "EOF"

usage: run-benchmark.sh [--unified] [--report-name name {helenus-benchmark}]
                        [--separate]
                        [--csv] [--json]

The script will output a single report file if --unified is used, otherwise one per benchmark (default).
This report can either be CSV (default) or JSON.

EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --report-name*|-n*)
      if [[ "$1" != *=* ]]; then shift; fi # Value is next arg if no `=`
      UNIFIED_REPORT_NAME="${1#*=}"
      ;;
    --unified*|-u*)
      UNIFIED_REPORT=true
      ;;
    --separate*|-s*)
      UNIFIED_REPORT=false
      ;;
    --csv*)
      REPORT_FORMAT="csv"
      ;;
    --json*)
      REPORT_FORMAT="json"
      ;;
    --help|-h)
      print_help
      exit 0
      ;;
    *)
      print_help
      exit 0
      ;;
  esac
  shift
done

cat << "EOF"

██╗  ██╗███████╗██╗     ███████╗███╗   ██╗██╗   ██╗███████╗
██║  ██║██╔════╝██║     ██╔════╝████╗  ██║██║   ██║██╔════╝
███████║█████╗  ██║     █████╗  ██╔██╗ ██║██║   ██║███████╗
██╔══██║██╔══╝  ██║     ██╔══╝  ██║╚██╗██║██║   ██║╚════██║
██║  ██║███████╗███████╗███████╗██║ ╚████║╚██████╔╝███████║
╚═╝  ╚═╝╚══════╝╚══════╝╚══════╝╚═╝  ╚═══╝ ╚═════╝ ╚══════╝

██████╗ ███████╗███╗   ██╗ ██████╗██╗  ██╗███╗   ███╗ █████╗ ██████╗ ██╗  ██╗
██╔══██╗██╔════╝████╗  ██║██╔════╝██║  ██║████╗ ████║██╔══██╗██╔══██╗██║ ██╔╝
██████╔╝█████╗  ██╔██╗ ██║██║     ███████║██╔████╔██║███████║██████╔╝█████╔╝
██╔══██╗██╔══╝  ██║╚██╗██║██║     ██╔══██║██║╚██╔╝██║██╔══██║██╔══██╗██╔═██╗
██████╔╝███████╗██║ ╚████║╚██████╗██║  ██║██║ ╚═╝ ██║██║  ██║██║  ██║██║  ██╗
╚═════╝ ╚══════╝╚═╝  ╚═══╝ ╚═════╝╚═╝  ╚═╝╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝

EOF

if [[ $UNIFIED_REPORT = true ]]; then
  unified_report
else
  separate_reports
fi