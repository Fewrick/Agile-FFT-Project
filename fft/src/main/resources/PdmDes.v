`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: Xilinx Inc.
// Engineer: Parimal Patel
// Create Date: 06/21/2016 09:22:41 AM
// Module Name: PdmDes
// Project Name: PYNQ
// Link: https://github.com/Xilinx/PYNQ/blob/master/boards/ip/audio_direct_1.1/src/PdmDes.v?utm_source=chatgpt.com
//////////////////////////////////////////////////////////////////////////////////


module PdmDes(
    input clk,
    input en,
    output done,
    output reg [15:0] dout,
    output pdm_m_clk_o,
    input pdm_m_data_i
    );

reg en_int=0;
reg done_int=0;
reg clk_int=0;
reg pdm_clk_rising=0;
reg [15:0] pdm_tmp=0;
integer cnt_bits=0;
integer cnt_clk=0;

initial dout=0;

assign done = done_int;
assign pdm_m_clk_o = clk_int;

// register en input    
always @(posedge clk)
    en_int <= en;

// Sample input serial data process
always @(posedge clk) 
  if (en==0)
    pdm_tmp <= 0;
  else
  if (pdm_clk_rising) 
     pdm_tmp <= {pdm_tmp[14:0],pdm_m_data_i};

// Count the number of sampled bits
always @(posedge clk)
begin
  if (en_int==0)
    cnt_bits <=0;
  else
    if (pdm_clk_rising)
    begin
        if (cnt_bits == 15)
            cnt_bits <=0;
        else
            cnt_bits <= cnt_bits + 1;
     end           
end

// Assert done only after all 16 bits, including the current input bit, are present.
always @(posedge clk)
begin
  done_int <= 0;
  if (en_int && pdm_clk_rising && cnt_bits == 15) begin
    done_int <= 1;
    dout <= {pdm_tmp[14:0], pdm_m_data_i};
  end
end

// Generate PDM Clock, that runs independent from the enable signal, therefore
// the onboard microphone will always send data. The 25-cycle half-period
// assumes a 100 MHz system clock and produces a 2 MHz PDM clock.
always @(posedge clk)
begin
  if (cnt_clk == 24) begin
    cnt_clk <= 0;
    clk_int <= ~clk_int;
    if (clk_int == 0)
      pdm_clk_rising <= 1;
    else
      pdm_clk_rising <= 0;
  end else begin
    cnt_clk <= cnt_clk + 1;
    pdm_clk_rising <= 0;
  end
end
    
endmodule