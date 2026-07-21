import { Controller, Get, NotFoundException, Param } from '@nestjs/common';
import { Instrument } from './instrument.model';
import { InstrumentsService } from './instruments.service';

@Controller('instruments')
export class InstrumentsController {
  constructor(private readonly instrumentsService: InstrumentsService) {}

  @Get()
  async findAll(): Promise<Instrument[]> {
    return this.instrumentsService.findAll();
  }

  @Get(':ticker')
  async findByTicker(@Param('ticker') ticker: string): Promise<Instrument> {
    const instrument = await this.instrumentsService.findByTicker(ticker);
    if (!instrument) {
      throw new NotFoundException(`Instrument ticker "${ticker}" not found.`);
    }
    return instrument;
  }
}
