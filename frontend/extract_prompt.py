import json

log_path = "/home/srivan/.gemini/antigravity/brain/6e875a34-7f5f-40f9-a951-504b43696fd5/.system_generated/logs/overview.txt"
output_path = "/home/srivan/Desktop/Chubby_Dolphin_AI/extracted_prompt.txt"

with open(log_path, "r") as f:
    for line in f:
        try:
            data = json.loads(line)
            if data.get("step_index") == 5450:
                with open(output_path, "w") as out:
                    out.write(data.get("content"))
                print("Successfully extracted prompt to", output_path)
                break
        except Exception as e:
            print("Error parsing line:", e)
