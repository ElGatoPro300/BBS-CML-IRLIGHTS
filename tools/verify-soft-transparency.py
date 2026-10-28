"""Numerical native GPU regression with an existing patched Complementary pack."""
import argparse
from pathlib import Path
from client_check import run_check

parser = argparse.ArgumentParser()
parser.add_argument("--mc")
parser.add_argument("--shaderpack", type=Path, required=True)
args = parser.parse_args()
run_check(args.mc, "soft-transparency", "tools/soft/SoftTransparencyClientCheck.java", "SoftTransparencyClientCheck", "PASS: soft transparency GPU blend", args.shaderpack)
