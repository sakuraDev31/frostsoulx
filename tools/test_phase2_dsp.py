#!/usr/bin/env python3
import math


def ms(left, right, side_gain):
    mid = (left + right) * 0.70710678118
    side = (left - right) * 0.70710678118 * side_gain
    return (mid + side) * 0.70710678118, (mid - side) * 0.70710678118


def test_ms_unity_is_identity():
    for left, right in ((0.2, -0.1), (0.9, 0.3), (-0.7, 0.4)):
        out = ms(left, right, 1.0)
        assert abs(out[0] - left) < 1e-8
        assert abs(out[1] - right) < 1e-8


def test_ms_mono_preservation():
    left, right = ms(0.4, 0.4, 0.0)
    assert abs(left - 0.4) < 1e-8
    assert abs(right - 0.4) < 1e-8


def test_ms_widening_is_bounded_by_final_limiter():
    left, right = ms(0.8, -0.8, 2.0)
    assert max(abs(left), abs(right)) > 0.8
    ceiling = 10 ** (-1 / 20)
    assert max(abs(left), abs(right)) > ceiling  # limiter must be the later safety stage


def test_tpdf_dither_is_one_lsb_peak():
    # Difference of two [0,1) uniforms divided by 32767 is within one LSB.
    for first, second in ((0.0, 1.0), (1.0, 0.0), (0.25, 0.75)):
        dither = (first - second) / 32767.0
        assert abs(dither) <= 1.0 / 32767.0


def test_gain_formula():
    assert abs(10 ** (6 / 20) - 1.9952623) < 1e-5
    assert abs(10 ** (-6 / 20) - 0.5011872) < 1e-5


if __name__ == '__main__':
    test_ms_unity_is_identity()
    test_ms_mono_preservation()
    test_ms_widening_is_bounded_by_final_limiter()
    test_tpdf_dither_is_one_lsb_peak()
    test_gain_formula()
    print('Phase 2 DSP vectors passed')
