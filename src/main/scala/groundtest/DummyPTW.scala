// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package groundtest

import Chisel._
import config._
import rocket._
import tile._
import util.ParameterizedBundle

class DummyPTW(n: Int)(implicit p: Parameters) extends CoreModule()(p) {
  val io = new Bundle {
    val requestors = Vec(n, new TLBPTWIO).flip
  }

  val req_arb = Module(new RRArbiter(new PTWReq, n))
  req_arb.io.in <> io.requestors.map(_.req)
  req_arb.io.out.ready := Bool(true)

  def vpn_to_ppn(vpn: UInt): UInt = vpn(ppnBits - 1, 0)

  class QueueChannel extends ParameterizedBundle()(p) {
    val ppn = UInt(width = ppnBits)
    val chosen = UInt(width = log2Up(n))
  }

  val s1_ppn = vpn_to_ppn(req_arb.io.out.bits.addr)
  val s2_ppn = RegEnable(s1_ppn, req_arb.io.out.valid)
  val s2_chosen = RegEnable(req_arb.io.chosen, req_arb.io.out.valid)
  val s2_valid = Reg(next = req_arb.io.out.valid)

  val s2_resp = Wire(new PTWResp)
  s2_resp.pte.ppn := s2_ppn
  s2_resp.pte.reserved_for_software := UInt(0)
  s2_resp.pte.d := Bool(true)
  s2_resp.pte.a := Bool(false)
  s2_resp.pte.g := Bool(false)
  s2_resp.pte.u := Bool(true)
  s2_resp.pte.r := Bool(true)
  s2_resp.pte.w := Bool(true)
  s2_resp.pte.x := Bool(false)
  s2_resp.pte.v := Bool(true)

  io.requestors.zipWithIndex.foreach { case (requestor, i) =>
    requestor.resp.valid := s2_valid && s2_chosen === UInt(i)
    requestor.resp.bits := s2_resp
    requestor.status.vm := UInt("b01000")
    requestor.status.prv := UInt(PRV.S)
    requestor.status.debug := Bool(false)
    requestor.status.mprv  := Bool(true)
    requestor.status.mpp := UInt(0)
    requestor.ptbr.asid := UInt(0)
    requestor.ptbr.ppn := UInt(0)
    requestor.invalidate := Bool(false)
  }
}
