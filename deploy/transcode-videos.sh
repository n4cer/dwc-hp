#!/usr/bin/env bash
# Converts the archive originals into browser-playable MP4s plus preview images.
#
# Browsers play neither Matroska nor the MPEG-2 video inside a DVD rip, so every
# original is re-encoded once to H.264/AAC. The originals are left untouched and
# stay available as a download.
#
# Layout produced inside the video folder:
#   <folder>/<name>.mkv      the untouched original
#   <folder>/web/<name>.mp4  what the <video> element plays
#   <folder>/posters/<name>.jpg
#
# Usage: ./transcode-videos.sh [video-folder]   (default: /var/lib/dwc/videos)

set -euo pipefail

folder="${1:-/var/lib/dwc/videos}"
web="$folder/web"
posters="$folder/posters"

for tool in ffmpeg ffprobe; do
  command -v "$tool" >/dev/null || { echo "$tool is not installed" >&2; exit 1; }
done
[[ -d "$folder" ]] || { echo "No such directory: $folder" >&2; exit 1; }

mkdir -p "$web" "$posters"

shopt -s nullglob nocaseglob
originals=("$folder"/*.mkv "$folder"/*.avi "$folder"/*.mpg "$folder"/*.mpeg "$folder"/*.wmv "$folder"/*.mov "$folder"/*.vob)
# nullglob must go too: it would silently drop any argument that contains glob
# characters and matches no file, such as ffmpeg's optional-stream "0:a:0?".
shopt -u nullglob nocaseglob
[[ ${#originals[@]} -gt 0 ]] || { echo "No source videos found in $folder" >&2; exit 1; }

probe() { ffprobe -v error -select_streams v:0 -show_entries "$1" -of default=nw=1:nk=1 "$2" 2>/dev/null | head -1; }

printf '%s\n' "Converting ${#originals[@]} video(s) from $folder"
printf '%s\n' "---"

for original in "${originals[@]}"; do
  name="$(basename "$original")"
  base="${name%.*}"
  target="$web/$base.mp4"
  poster="$posters/$base.jpg"

  if [[ -f "$target" ]]; then
    echo "Skipping $name (web/$base.mp4 already exists)"
  else
    # DVD sources are usually interlaced and always use non-square pixels.
    # yadif removes the combing, and baking the display aspect ratio into square
    # pixels stops browsers from showing the picture stretched.
    filters="scale='trunc(ih*dar/2)*2:trunc(ih/2)*2',setsar=1"
    field_order="$(probe stream=field_order "$original")"
    case "$field_order" in
      tt|bb|tb|bt) filters="yadif=mode=1,$filters"; echo "$name: interlaced ($field_order), deinterlacing" ;;
    esac

    echo "$name -> web/$base.mp4"
    ffmpeg -nostdin -loglevel warning -stats -i "$original" \
      -map 0:v:0 -map "0:a:0?" \
      -vf "$filters" \
      -c:v libx264 -preset slow -crf 20 -profile:v high -pix_fmt yuv420p \
      -c:a aac -b:a 160k -ac 2 \
      -movflags +faststart \
      -f mp4 \
      -y "$target.part"
    # Only now is the file complete: an interrupted encode leaves behind a
    # .part file, which the skip check above deliberately ignores.
    mv -f "$target.part" "$target"
  fi

  if [[ -f "$poster" ]]; then
    echo "Skipping poster for $name (posters/$base.jpg already exists)"
  else
    # A frame from 10% in: far enough past black intros to show something useful.
    duration="$(probe format=duration "$target" || true)"
    [[ -n "$duration" ]] || duration="$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$target")"
    offset="$(awk -v d="$duration" 'BEGIN { printf "%.2f", (d > 0 ? d * 0.1 : 0) }')"
    ffmpeg -nostdin -loglevel error -ss "$offset" -i "$target" -frames:v 1 -vf "scale=480:-2" -q:v 3 -y "$poster"
  fi
done

printf '%s\n' "---"
printf '%s\n' "Done. Values for the admin form (/admin/videos):"
printf '%s\n' "---"
printf '%-28s %-28s %-24s %s\n' "MP4 file in web/" "Poster in posters/" "Original for download" "Duration in seconds"
for original in "${originals[@]}"; do
  name="$(basename "$original")"
  base="${name%.*}"
  [[ -f "$web/$base.mp4" ]] || continue
  seconds="$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$web/$base.mp4" | awk '{ printf "%d", $1 + 0.5 }')"
  printf '%-28s %-28s %-24s %s\n' "$base.mp4" "$base.jpg" "$name" "$seconds"
done

printf '%s\n' "---"
printf '%s\n' "Make sure the files are readable by the application user, e.g.:"
printf '%s\n' "  chmod -R a+rX $folder"
