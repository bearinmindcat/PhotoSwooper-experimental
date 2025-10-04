#!/bin/bash

SILENT_LOG=/tmp/silent_log_$$.txt
trap "/bin/rm -f $SILENT_LOG" EXIT

function report_and_exit {
    cat "${SILENT_LOG}";
    echo "\033[91mError running command.\033[39m"
    exit 1;
}

function silent {
    $* 2>>"${SILENT_LOG}" >> "${SILENT_LOG}" || report_and_exit;
}

spinner()
{
  sleep 1s
  pid=$! # Process Id of the previous running command
  spin='-\|/'

  i=0
  while kill -0 $pid 2>/dev/null
  do
    i=$(( (i+1) %4 ))
    printf "\r${spin:$i:1}"
    sleep .1
  done
}

Help() {
  echo
  echo -k Path to java keystore to sign with
  echo -u link to git repo of the app to build
  echo -t Commit tag to build from \(default: Most recent tag\)
  echo -o Output file name/path \(default: ~/Downloads/app-release.apk
  echo -h Displays this help message
  echo
}

if [ "$1" == "-h" ]; then
  Help
  exit 0
fi

while getopts ":k:u:t:o:" option; do
   case $option in
      k) keystore_path=$OPTARG;;
      u) git_url=$OPTARG;;
      t) commit_tag=$OPTARG;;
      o) output_file=$OPTARG;;
      \?) # incorrect option
         echo "Error: Invalid option: -${OPTARG}"
         exit;;
   esac
done

#=================================================
# SET DEFAULT VALUES
#=================================================
if [[ $git_url = "" ]]; then
  echo Please enter the git url of the app you want to build:
	read -r git_url
fi
if [[ $commit_tag = "" ]]; then
  if [ -d ".git" ]; then
    commit_tag=$(git describe --tags "$(git rev-list --tags --max-count=1)")
  else
    echo For the latest tag to be automatically used, this script must be run from the local repository root directory.
    echo Please manually enter the desired tag to build from here:
    read -r commit_tag
  fi


fi
if [[ $output_file = "" ]]; then
	output_file=~/Downloads/app-release.apk
fi
if [[ $keystore_path = "" ]]; then
	echo Please enter the file path of the java keystore to sign the app with:
  read -r keystore_path
fi

#=================================================
# SETUP FILES
#=================================================
echo
echo "1/3 Cloning git repo and checking out tag ${commit_tag}..."
tempdir=$(mktemp -d "${TMPDIR:-/tmp/}$(basename "$0").XXXXXXXXXXXX")
cd "$tempdir"
mkdir repository
silent git clone --branch $commit_tag --depth 1 "$git_url" repository & spinner
cd repository

#=================================================
# BUILD
#=================================================
echo
echo 2/3 Building your app...
export "ANDROID_HOME=/home/loowiz/Android/Sdk"
silent ./gradlew clean assembleRelease --no-build-cache --no-configuration-cache --no-daemon  & spinner
cd "$tempdir/repository/app/build/outputs/apk/release/"

#=================================================
# SIGN
#=================================================
echo
echo "3/3 Signing your apk file"
echo
silent zipalign -v -p 4 app-release-unsigned.apk app-release-unsigned-aligned.apk & spinner
apksigner sign --ks "$keystore_path" --out "$output_file" "$tempdir/repository/app/build/outputs/apk/release/app-release-unsigned-aligned.apk" && echo Your signed apk file can be found at $output_file
rm -rf $tempdir
exit
