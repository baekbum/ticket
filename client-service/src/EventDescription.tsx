import { ticketAssetUrl } from './ticketAssetUrl';

function getDescriptionSections(description: string) {
  return description.trim().split(/(?=^##[ \t]+)/m).map((section) => {
    const title = /^##[ \t]+([^\r\n]+)(?:\r?\n|$)/.exec(section);
    const content = title ? section.slice(title[0].length) : section;

    return {
      title: title?.[1].trim(),
      paragraphs: content.split(/\r?\n(?:[ \t]*\r?\n)+/).map((paragraph) => paragraph.trim()).filter(Boolean),
    };
  }).filter(({ paragraphs }) => paragraphs.length > 0);
}

export default function EventDescription({ description, posterUrl }: {
  description: string | null;
  posterUrl: string;
}) {
  const [intro, ...highlights] = getDescriptionSections(description ?? '');
  if (!intro) return null;

  return (
    <section className="event-description-panel" aria-labelledby="event-description-title">
      <h2 id="event-description-title">공연 소개</h2>
      {intro.title ? (
        <div className="event-description-hero">
          <div className="event-description-copy">
            <h3>{intro.title}</h3>
            {intro.paragraphs.map((paragraph, index) => <p key={index}>{paragraph}</p>)}
          </div>
          {posterUrl && (
            <img src={ticketAssetUrl(posterUrl)} alt="" loading="lazy" decoding="async" />
          )}
        </div>
      ) : (
        <div className="event-description-prose">
          {intro.paragraphs.map((paragraph, index) => <p key={index}>{paragraph}</p>)}
        </div>
      )}
      {highlights.length > 0 && (
        <div className="event-description-highlights">
          <h3>관람 포인트</h3>
          <div className="event-description-grid">
            {highlights.map(({ title, paragraphs }, index) => (
              <div className="event-description-point" key={index}>
                {title && <h4>{title}</h4>}
                {paragraphs.map((paragraph, paragraphIndex) => <p key={paragraphIndex}>{paragraph}</p>)}
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}
