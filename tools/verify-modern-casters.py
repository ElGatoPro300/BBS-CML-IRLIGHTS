"""Verify actual CML geometry depth in the modern Core atlas."""
import argparse
from client_check import run_check
parser = argparse.ArgumentParser()
parser.add_argument("--mc", required=True)
args = parser.parse_args()
run_check(args.mc, "modern-casters", "tools/modern/CasterDepthClientCheck.java", "CasterDepthClientCheck", "PASS: native CML caster depth")
