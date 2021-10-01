import sbt.VirtualAxis

sealed abstract class JSDomVersion(val idSuffix: String, val directorySuffix: String)
    extends VirtualAxis.WeakAxis

object JSDomVersion {
  case object V1 extends JSDomVersion("-JsDom1", "-jsdom1")
  case object V2 extends JSDomVersion("-JsDom2", "-jsdom2")
}
