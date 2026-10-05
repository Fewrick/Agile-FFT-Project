## Completed Chisel implementation

This implementation was developed with the assistance of AI tools. All AI-assisted contributions were reviewed, verified, and incorporated by the group.

`CsrDescription.scala` parses the workbook at elaboration time into blocks,
registers and fields. It checks field widths, initial values, aligned addresses,
duplicate names/addresses and conflicting bit ranges. Read-only and write-only
fields may share bits, as UART RX and TX do in `soc.xlsx`.

`CsrAdapter.scala` generates the specified `csr.block.register.field` hierarchy
using `DynamicBundle`. Whole-register signals omit the field level, trigger
signals contain `data` and `trg`, and constants have no hardware ports. Blocks
containing only constants are omitted from `csr`.

The APB interface completes every transfer in its first access cycle
(`psel && penable`). Writes take effect at the closing clock edge. Invalid
addresses and disallowed access directions complete with `pslverr`; unused read
bits are zero. Setup/idle/reset cannot write or trigger. Mixed registers accept
a direction if at least one field permits it, and only those fields participate.

`rw` and `wotrg` fields use registers. Explicit `Init` values are synchronously
reset; `?` means no reset value and must not be relied on before a write.
Following the Python reference, `wotrg.trg` is asserted in the write access
cycle and `wotrg.data` changes at its closing edge, retaining the last value
afterward. Consequently, hardware sampling `data` on the same edge as `trg`
sees the previous value; consumers must account for this reference timing.
`rotrg.trg` is asserted during the read access cycle and its data is combinational.
Unlike the reference's simplified APB logic, the Chisel implementation explicitly
qualifies accesses with `penable` and assigns all response outputs in every cycle.

Run from the project root:

```bash
# Chisel-only regression (does not require Verilator):
sbt 'testOnly CsrAdapterTest GeneratorTest -- -z Chisel'

# Generate the reference RTL, then run all tests (requires Verilator):
python3 csr_adapter_gen.py soc.xlsx
sbt test

# Generate generated/CsrAdapter.sv using the Chisel 6 CIRCT stage:
sbt 'runMain CsrAdapter'
```

`CsrAdapterTest.scala` covers reset, seeded random read/write sequences, field
masking, independent instances, live hardware inputs, access permissions,
unmapped/unaligned addresses, trigger timing, back-to-back transfers, and setup,
idle and reset gating. A temporary second workbook tests arbitrary names,
nonzero bit offsets, mixed register types and whole-register triggers. The
original Python reference test remains unchanged. The BFM bounds APB waits at
100 cycles so handshake failures cannot hang a test.
