import subprocess
import os

def run_cmd(args, cwd=None):
    print(f"Running: {' '.join(args)} in {cwd or os.getcwd()}")
    try:
        res = subprocess.run(args, capture_output=True, text=True, cwd=cwd)
        print("STDOUT:")
        print(res.stdout)
        print("STDERR:")
        print(res.stderr)
        print("EXIT CODE:", res.returncode)
        print("-" * 50)
        return res.returncode
    except Exception as e:
        print("Error executing:", e)
        print("-" * 50)
        return -1

# Test mvn version
run_cmd(["mvn", "--version"])
