import json
import os

log_path = '/home/srivan/.gemini/antigravity/brain/6e875a34-7f5f-40f9-a951-504b43696fd5/.system_generated/logs/overview.txt'
out_path = '/home/srivan/Desktop/Chubby_Dolphin_AI/frontend/extracted_user_request.txt'

if os.path.exists(log_path):
    with open(log_path, 'r') as f:
        lines = f.readlines()
    
    target_line = None
    for line in lines:
        if "MASTER PRODUCT AUDIT" in line and "USER_INPUT" in line:
            target_line = line
            break
            
    if target_line:
        data = json.loads(target_line)
        with open(out_path, 'w') as f_out:
            f_out.write(data['content'])
        print("Success! Extracted to", out_path)
    else:
        print("Target line not found.")
else:
    print("Log path does not exist.")
