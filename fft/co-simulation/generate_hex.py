"""Deterministic signed 8-bit waveform vectors, independent of FFT processing."""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np

DIRECTORY = Path(__file__).resolve().parent / 'generated_hex'
# Frequency is cycles per vector; phase is radians. Periodic shapes start at phase zero.
WAVEFORMS = {
    'sine': lambda angle: np.sin(angle),
    'cosine': lambda angle: np.cos(angle),
    'square': lambda angle: np.where(np.remainder(angle, 2 * np.pi) < np.pi, 1., -1.),
    'triangle': lambda angle: 2 / np.pi * np.arcsin(np.sin(angle)),
    'sawtooth': lambda angle: 2 * np.remainder(angle / (2 * np.pi), 1) - 1,
    'constant': None,
    'impulse': None,
    'noise': None,
}


def generate_waveform(waveform='sine', samples=32, amplitude=127, frequency=2,
                      phase=0, seed=0, position=0):
    """Return floating-point samples. Noise is uniform white noise in [-amplitude, amplitude)."""
    if waveform not in WAVEFORMS:
        raise ValueError(f'Unknown waveform: {waveform}')
    if not isinstance(samples, (int, np.integer)) or samples <= 0:
        raise ValueError('samples must be a positive integer')
    if not all(np.isfinite(v) for v in (amplitude, frequency, phase)):
        raise ValueError('amplitude, frequency and phase must be finite')
    if not 0 <= frequency <= samples / 2:
        raise ValueError('frequency must be between zero and Nyquist (samples / 2)')
    if not isinstance(seed, (int, np.integer)) or seed < 0:
        raise ValueError('seed must be a nonnegative integer')
    if not isinstance(position, (int, np.integer)) or not 0 <= position < samples:
        raise ValueError('impulse position must be an index within the vector')
    if waveform == 'constant':
        return np.full(samples, amplitude, dtype=float)
    if waveform == 'impulse':
        result = np.zeros(samples)
        result[position] = amplitude
        return result
    if waveform == 'noise':
        if amplitude < 0:
            raise ValueError('noise amplitude must be nonnegative')
        return amplitude * np.random.default_rng(seed).uniform(-1, 1, samples)
    # Keep the legacy sine expression and operation ordering, including zero phase.
    angle = 2 * np.pi * frequency * np.arange(samples) / samples
    if phase != 0:
        angle = angle + phase
    return amplitude * WAVEFORMS[waveform](angle)


def quantize(samples):
    """Round to nearest even, rejecting overflow rather than clipping or wrapping."""
    values = np.asarray(samples, dtype=float)
    if values.ndim != 1 or not len(values) or not np.all(np.isfinite(values)):
        raise ValueError('Expected a nonempty vector of finite samples')
    rounded = np.rint(values)
    if np.any(rounded < -128) or np.any(rounded > 127):
        raise ValueError('Samples exceed signed 8-bit range after rounding')
    return rounded.astype(np.int64)


def sine_input(samples=32, cycles=2, amplitude=127, integer_input=False):
    """Original sine, with the original floating-point calculation and rounding."""
    values = generate_waveform('sine', samples, amplitude, cycles)
    return quantize(values) if integer_input else values


def encode_hex(samples):
    """Encode already quantized signed bytes as one uppercase HEX byte per line."""
    values = np.asarray(samples)
    integers = quantize(values)
    if not np.array_equal(values, integers):
        raise ValueError('HEX encoding requires integer samples; call quantize first')
    return ''.join(f'{int(value) & 0xFF:02X}\n' for value in integers)


def write_hex(samples, output_path):
    path = Path(output_path)
    encoded = encode_hex(samples)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(encoded, encoding='ascii')
    return path


def vector_directory(samples, output_dir=None):
    """A custom output_dir is also a root containing one directory per sample count."""
    return (Path(output_dir).resolve() if output_dir is not None else DIRECTORY) / str(samples)


def _digest(text):
    return hashlib.sha256(text.encode('ascii')).hexdigest()


def _publish(vectors, samples, output_dir=None, replace=False):
    """Track ownership; replace removes stale owned vectors but preserves manual files."""
    directory = vector_directory(samples, output_dir)
    manifest = directory / '.generated.json'
    owned = json.loads(manifest.read_text()) if manifest.exists() else {}
    for name, digest in owned.items():
        if Path(name).name != name or not name.endswith('.hex'):
            raise ValueError('Invalid generated-vector manifest')
        path = directory / name
        if path.exists() and _digest(path.read_text()) != digest:
            raise ValueError(f'{path}: generated file was manually changed; move it before regenerating')
    for path in directory.glob('*.hex'):
        if path.name not in owned:
            lines = path.read_text().splitlines()
            if len(lines) != samples or any(len(line) != 2 or any(c not in '0123456789abcdefABCDEF' for c in line) for line in lines):
                raise ValueError(f'{path}: unrelated vector violates the {samples}-sample directory contract')
    if any(len(values) != samples for values in vectors.values()):
        raise ValueError(f'Every vector must contain exactly {samples} samples')
    encoded = {name: encode_hex(values) for name, values in vectors.items()}
    stale = set(owned) - set(encoded)
    changed = any(owned.get(name) != _digest(data) for name, data in encoded.items() if name in owned)
    if (stale or changed) and not replace:
        raise ValueError('Generated vector set changed; use --replace to regenerate it')
    for name, data in encoded.items():
        path = directory / name
        if path.exists() and name not in owned:
            raise ValueError(f'{path}: refusing to overwrite an unrelated file')
    directory.mkdir(parents=True, exist_ok=True)
    for name in stale:
        (directory / name).unlink(missing_ok=True)
    for name, data in encoded.items():
        (directory / name).write_text(data, encoding='ascii')
    manifest.write_text(json.dumps({name: _digest(data) for name, data in sorted(encoded.items())}, indent=2) + '\n')
    return [directory / name for name in sorted(encoded)]


def _representatives(samples, amplitude=127, frequency=None, phase=0, seed=0):
    frequency = min(2, samples // 2) if frequency is None else frequency
    return {f'{name}.hex': quantize(generate_waveform(name, samples, amplitude, frequency, phase, seed))
            for name in WAVEFORMS}


def generate_all(samples=32, output_dir=None, replace=False, **parameters):
    """Generate one representative of every registered waveform."""
    return _publish(_representatives(samples, **parameters), samples, output_dir, replace)


def generate_suite(samples=32, output_dir=None, replace=False):
    """Generate representatives plus distinct periodic, mixed, DC and boundary cases."""
    vectors = _representatives(samples)
    seen = {encode_hex(values) for values in vectors.values()}

    def add(name, values):
        values = quantize(values)
        encoded = encode_hex(values)
        if encoded not in seen:
            vectors[name + '.hex'] = values
            seen.add(encoded)

    for waveform, generator in WAVEFORMS.items():
        if generator is not None:
            for frequency, amplitude, phase in [(1, 64, 0), (3, 127, 0), (1, 32, np.pi / 2)]:
                if frequency <= samples / 2:
                    add(f'{waveform}_f{frequency}_a{amplitude}_p{phase:.6g}',
                        generate_waveform(waveform, samples, amplitude, frequency, phase))
    for value, label in [(0, 'zero'), (127, 'max'), (-128, 'min')]:
        add(f'constant_{label}', generate_waveform('constant', samples, value, 0))
    add('impulse_center_a127', generate_waveform('impulse', samples, frequency=0, position=samples // 2))
    add('alternating_extremes', np.where(np.arange(samples) % 2, -128, 127))
    add('noise_seed42', generate_waveform('noise', samples, frequency=0, seed=42))
    if samples >= 4:
        add('mixed_f1_f2_a48', generate_waveform('sine', samples, 48, 1)
            + generate_waveform('cosine', samples, 48, 2))
    return _publish(vectors, samples, output_dir, replace)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--waveform', choices=WAVEFORMS)
    mode.add_argument('--all', action='store_true')
    mode.add_argument('--suite', action='store_true')
    parser.add_argument('--samples', type=int, default=32)
    parser.add_argument('--frequency', '--cycles', dest='frequency', type=float, default=None)
    parser.add_argument('--amplitude', type=float, default=127)
    parser.add_argument('--phase', type=float, default=0)
    parser.add_argument('--seed', type=int, default=0)
    parser.add_argument('--position', type=int, default=0)
    parser.add_argument('--output', help='Single waveform filename within the sample-count directory')
    parser.add_argument('--output-dir', type=Path, help='Alternate regression root (contains sample-count directories)')
    parser.add_argument('--replace', action='store_true', help='Replace changed/stale owned batch vectors')
    args = parser.parse_args()
    try:
        if args.samples <= 0:
            raise ValueError('samples must be positive')
        if args.all or args.suite:
            if args.output or args.position != 0:
                raise ValueError('--output and --position apply only to single waveforms')
            if args.suite:
                if (args.frequency, args.amplitude, args.phase, args.seed) != (None, 127, 0, 0):
                    raise ValueError('--suite uses fixed explicit parameters; use --all for custom parameters')
                paths = generate_suite(args.samples, args.output_dir, args.replace)
            else:
                paths = generate_all(args.samples, args.output_dir, args.replace,
                                     amplitude=args.amplitude, frequency=args.frequency, phase=args.phase, seed=args.seed)
        else:
            name = args.output or f'{args.waveform or "sine"}.hex'
            if Path(name).name != name or not name.endswith('.hex'):
                raise ValueError('--output must be a filename ending in .hex; use --output-dir for directories')
            values = quantize(generate_waveform(args.waveform or 'sine', args.samples, args.amplitude,
                              2 if args.frequency is None else args.frequency, args.phase, args.seed, args.position))
            # Single generation updates one owned vector, preserving the rest of a suite.
            directory = vector_directory(args.samples, args.output_dir)
            manifest = directory / '.generated.json'
            owned = json.loads(manifest.read_text()) if manifest.exists() else {}
            vectors = {key: np.array([int(line, 16) - (256 if int(line, 16) >= 128 else 0)
                       for line in (directory / key).read_text().splitlines()])
                       for key in owned if (directory / key).exists()}
            vectors[name] = values
            paths = _publish(vectors, args.samples, args.output_dir, args.replace)
        for path in paths:
            print(f'Generated {path}')
    except (ValueError, OSError) as error:
        parser.error(str(error))


if __name__ == '__main__':
    main()
