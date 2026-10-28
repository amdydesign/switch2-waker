"""Switch 2 Waker - helper functions for spoofing a Joy-Con 2 wake beacon.

Header-only component: adding `switch2_waker:` to your config makes the
s2_* helpers available to lambdas.
"""
import esphome.codegen as cg
import esphome.config_validation as cv

CODEOWNERS = ["@amdydesign"]
DEPENDENCIES = ["esp32_ble"]

CONFIG_SCHEMA = cv.Schema({})


async def to_code(config):
    cg.add_global(cg.RawStatement('#include "esphome/components/switch2_waker/switch2_waker.h"'))
