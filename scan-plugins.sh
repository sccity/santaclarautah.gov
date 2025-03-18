#!/bin/bash

echo "WordPress Plugins Manifest"
echo "Generated: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
echo
printf "%-50s %-15s %-20s\n" "PLUGIN" "SIZE" "LAST MODIFIED"
printf "%-50s %-15s %-20s\n" "$(printf '%50s' | tr ' ' '-')" "$(printf '%15s' | tr ' ' '-')" "$(printf '%20s' | tr ' ' '-')"

total_size=0

# Function to convert bytes to human readable format
human_readable() {
    local bytes=$1
    if [[ $bytes -lt 1024 ]]; then
        echo "${bytes}B"
    elif [[ $bytes -lt 1048576 ]]; then
        echo "$(( (bytes + 512) / 1024 ))KB"
    else
        echo "$(( (bytes + 524288) / 1048576 ))MB"
    fi
}

# Detect OS and set stat command accordingly
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    get_size() {
        stat -f %z "$1"
    }
    get_date() {
        stat -f "%Sm" -t "%Y-%m-%d %H:%M" "$1"
    }
else
    # Linux
    get_size() {
        stat --format=%s "$1"
    }
    get_date() {
        stat --format=%y "$1" | cut -d. -f1
    }
fi

# Process each zip and tar file
while IFS= read -r file; do
    if [[ -f "$file" ]]; then
        size=$(get_size "$file")
        total_size=$((total_size + size))
        modified=$(get_date "$file")
        filename=$(basename "$file")
        human_size=$(human_readable $size)
        printf "%-50s %-15s %-20s\n" "${filename:0:47}..." "$human_size" "$modified"
    fi
done < <(find plugins -maxdepth 1 -type f \( -name "*.zip" -o -name "*.tar" -o -name "*.tar.gz" -o -name "*.tgz" \))

echo
echo "----------------------------------------"
total_human=$(human_readable $total_size)
echo "Total size of all plugins: $total_human"
echo "Total number of plugins: $(find plugins -maxdepth 1 -type f \( -name "*.zip" -o -name "*.tar" -o -name "*.tar.gz" -o -name "*.tgz" \) | wc -l | tr -d ' ')" 