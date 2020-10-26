import ammonite.ops._
implicit val wd = pwd

@main
def clean(
    images: Boolean @doc("Delete all images") = false,
    volumes: Boolean @doc("Delete all volumes") = false
) = {
  val imgs =
    (%% docker ('images)).out.lines.tail zip
      (%% docker ('images, "-q")).out.lines

  for {
    (imageDesc, imageId) <- imgs
    if imageDesc startsWith "<none>"
  } % docker ('image, 'rm, "--force", imageId)

  val containers = (%% docker ('ps, "-q", "-a")).out.lines

  for (c <- containers) % docker ('container, 'rm, "-f", c)

  if (images)
    for ((_, image) <- imgs) % docker ('image, 'rm, "--force", image)

  if (volumes) {
    val volumes = (%% docker ('volume, 'ls, "-q")).out.lines
    volumes.foreach(v => % docker ('volume, 'rm, v))
  }
}
