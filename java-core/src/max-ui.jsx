import { MaxUI, Panel, Button } from '@maxhub/max-ui';
import '@maxhub/max-ui/dist/styles.css';

function AdminApp() {
  return (
    <MaxUI resetBody>
      <Panel>
        <Button>Принять сигнал</Button>
      </Panel>
    </MaxUI>
  );
}